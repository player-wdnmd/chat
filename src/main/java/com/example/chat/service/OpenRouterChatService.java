package com.example.chat.service;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.example.chat.config.ChatAppProperties;
import com.example.chat.config.OpenRouterProperties;
import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatRequest;
import com.example.chat.model.ChatResponse;
import com.example.chat.model.OpenRouterRequest;
import com.example.chat.model.OpenRouterResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 聊天主服务，负责：
 * 1. 组装最终发给模型的消息窗口；
 * 2. 调用 OpenRouter；
 * 3. 在成功时返回剩余积分；
 * 4. 在失败时退回已扣减的积分。
 *
 * <p>当前网络调用使用 Hutool HTTP，不再依赖 Spring RestClient。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class OpenRouterChatService {

    private final OpenRouterProperties properties;
    private final ChatAppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final ChatSkillService chatSkillService;

    public ChatResponse chat(AuthenticatedUser user, ChatRequest request) {
        long startedAt = System.currentTimeMillis();
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("还没有配置 OPENROUTER_API_KEY，请先设置环境变量后再启动服务。");
        }

        List<ChatMessage> normalizedMessages = normalizeMessages(user.id(), request.messages(), request.skillIds());
        if (normalizedMessages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空。");
        }

        String primaryModel = resolveModel(properties.getDefaultModel(), "openrouter.default-model");
        String fallbackModel = normalizeModel(properties.getFallbackModel());
        log.info(
                "准备调用 OpenRouter：conversationId={}，model={}，fallbackModel={}，messageCount={}，skillCount={}",
                request.conversationId(),
                primaryModel,
                fallbackModel,
                normalizedMessages.size(),
                chatSkillService.normalizeSkillIds(user.id(), request.skillIds()).size()
        );

        int chatCost = appProperties.getChat().getCostPerRequest();
        int remainingPoints = authService.consumePoints(user.id(), chatCost);
        try {
            List<OpenRouterRequest.Message> requestMessages = normalizedMessages.stream()
                    .map(message -> new OpenRouterRequest.Message(message.role(), message.content()))
                    .toList();
            OpenRouterResponse response = sendRequestWithFallback(primaryModel, fallbackModel, requestMessages);
            String resolvedResponseModel = response.model() != null ? response.model() : primaryModel;
            String content = extractContent(response);
            OpenRouterResponse.Usage usage = response.usage();
            long latencyMs = System.currentTimeMillis() - startedAt;
            ChatResponse chatResponse = new ChatResponse(
                    resolvedResponseModel,
                    content,
                    response.id(),
                    latencyMs,
                    remainingPoints,
                    usage == null
                            ? null
                            : new ChatResponse.TokenUsage(
                            usage.promptTokens(),
                            usage.completionTokens(),
                            usage.totalTokens()
                    )
            );
            log.info(
                    "OpenRouter 响应成功：userId={}，requestId={}，model={}，latencyMs={}，totalTokens={}，remainingPoints={}，contentLength={}",
                    user.id(),
                    chatResponse.requestId(),
                    chatResponse.model(),
                    latencyMs,
                    usage == null ? null : usage.totalTokens(),
                    remainingPoints,
                    chatResponse.content().length()
            );
            return chatResponse;
        } catch (RuntimeException exception) {
            authService.refundPoints(user.id(), chatCost);
            throw exception;
        }
    }

    /**
     * 构造最终消息窗口：
     * 前端 system 消息 -> skill system 消息 -> 最近对话历史。
     */
    private List<ChatMessage> normalizeMessages(Long userId, List<ChatMessage> incomingMessages, List<String> requestedSkillIds) {
        List<ChatMessage> systemMessages = incomingMessages.stream()
                .filter(message -> "system".equalsIgnoreCase(message.role()))
                .filter(message -> StringUtils.hasText(message.content()))
                .limit(4)
                .toList();
        List<ChatMessage> skillMessages = chatSkillService.buildSkillMessages(userId, requestedSkillIds);

        List<ChatMessage> dialogueMessages = incomingMessages.stream()
                .filter(message -> !"system".equalsIgnoreCase(message.role()))
                .filter(message -> StringUtils.hasText(message.content()))
                .toList();

        int contextLimit = resolveContextLimit();
        int fromIndex = Math.max(0, dialogueMessages.size() - contextLimit);
        List<ChatMessage> trimmed = new ArrayList<>(systemMessages.size() + skillMessages.size() + contextLimit);
        trimmed.addAll(systemMessages);
        trimmed.addAll(skillMessages);
        trimmed.addAll(dialogueMessages.subList(fromIndex, dialogueMessages.size()));
        return trimmed;
    }

    private int resolveContextLimit() {
        Integer configuredLimit = properties.getMaxContextMessages();
        if (configuredLimit == null || configuredLimit <= 0) {
            throw new IllegalStateException("还没有配置有效的 openrouter.max-context-messages。");
        }
        return configuredLimit;
    }

    private String resolveModel(String configuredValue, String propertyName) {
        if (!StringUtils.hasText(configuredValue)) {
            throw new IllegalStateException("还没有配置 " + propertyName + "。");
        }
        return configuredValue.trim();
    }

    private String normalizeModel(String model) {
        return StringUtils.hasText(model) ? model.trim() : "";
    }

    private OpenRouterResponse sendRequestWithFallback(
            String primaryModel,
            String fallbackModel,
            List<OpenRouterRequest.Message> messages
    ) {
        List<String> candidates = new ArrayList<>();
        candidates.add(primaryModel);
        if (StringUtils.hasText(fallbackModel) && !fallbackModel.equals(primaryModel)) {
            candidates.add(fallbackModel);
        }

        OpenRouterException lastException = null;
        for (int i = 0; i < candidates.size(); i++) {
            String candidateModel = candidates.get(i);
            OpenRouterRequest payload = buildRequest(candidateModel, messages);
            try {
                return sendRequestWithRetry(payload);
            } catch (OpenRouterException exception) {
                lastException = exception;
                if (i >= candidates.size() - 1 || !exception.isRetryable()) {
                    throw exception;
                }
                log.warn(
                        "OpenRouter 主模型调用失败，准备切换回退模型：fromModel={}，toModel={}，reason={}",
                        candidateModel,
                        candidates.get(i + 1),
                        exception.getMessage()
                );
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new OpenRouterException("OpenRouter 请求失败，未能获得有效响应。");
    }

    private OpenRouterRequest buildRequest(String model, List<OpenRouterRequest.Message> messages) {
        return new OpenRouterRequest(
                model,
                messages,
                properties.getTemperature(),
                properties.getMaxTokens(),
                Boolean.FALSE
        );
    }

    private OpenRouterResponse sendRequestWithRetry(OpenRouterRequest payload) {
        int maxRetries = properties.getMaxRetries() == null || properties.getMaxRetries() < 0 ? 1 : properties.getMaxRetries();
        long retryDelayMillis = properties.getRetryDelay() == null ? 600L : properties.getRetryDelay().toMillis();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return sendRequestOnce(payload);
            } catch (OpenRouterException exception) {
                if (!exception.isRetryable() || attempt >= maxRetries) {
                    throw exception;
                }
                log.warn(
                        "OpenRouter 请求失败，准备重试：model={}，attempt={}，maxRetries={}，reason={}",
                        payload.model(),
                        attempt + 1,
                        maxRetries,
                        exception.getMessage()
                );
                sleepBeforeRetry(retryDelayMillis);
            }
        }

        throw new OpenRouterException("OpenRouter 请求失败，重试后仍未成功。");
    }

    private void sleepBeforeRetry(long retryDelayMillis) {
        if (retryDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenRouterException("OpenRouter 重试等待被中断。", HttpStatus.BAD_GATEWAY, false, exception);
        }
    }

    /**
     * 使用 Hutool HTTP 发起 OpenRouter 请求。
     */
    private OpenRouterResponse sendRequestOnce(OpenRouterRequest payload) {
        try {
            String requestJson = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.post(properties.getBaseUrl() + "/chat/completions")
                    .contentType(ContentType.JSON.toString())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestJson)
                    .setConnectionTimeout((int) properties.getConnectTimeout().toMillis())
                    .setReadTimeout((int) properties.getReadTimeout().toMillis());

            if (StringUtils.hasText(properties.getSiteUrl())) {
                request.header("HTTP-Referer", properties.getSiteUrl());
            }
            if (StringUtils.hasText(properties.getAppName())) {
                request.header("X-Title", properties.getAppName());
                request.header("X-OpenRouter-Title", properties.getAppName());
            }

            try (HttpResponse response = request.execute()) {
                String responseBody = response.body();
                if (response.getStatus() < 200 || response.getStatus() >= 300) {
                    log.warn("OpenRouter 返回错误响应：status={}，body={}", response.getStatus(), responseBody);
                    HttpStatus status = HttpStatus.resolve(response.getStatus());
                    boolean retryable = status != null && status.is5xxServerError();
                    throw new OpenRouterException(
                            parseErrorMessage(responseBody, response.getStatus()),
                            status == null ? HttpStatus.BAD_GATEWAY : status,
                            retryable
                    );
                }
                return objectMapper.readValue(responseBody, OpenRouterResponse.class);
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化或解析 OpenRouter 请求/响应失败。", exception);
        } catch (HttpException exception) {
            log.error("调用 OpenRouter 时发生网络或传输层异常", exception);
            throw new OpenRouterException("调用 OpenRouter 失败，请检查网络或模型配置。", exception);
        }
    }

    private String extractContent(OpenRouterResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new OpenRouterException("OpenRouter 返回成功，但响应体里没有 choices。");
        }

        OpenRouterResponse.Message message = response.choices().getFirst().message();
        if (message == null) {
            throw new OpenRouterException("OpenRouter 返回成功，但没有 message 内容。");
        }

        String text = flattenContent(message.content());
        if (!StringUtils.hasText(text)) {
            throw new OpenRouterException("OpenRouter 返回成功，但没有可显示的文本内容。");
        }
        return text.trim();
    }

    private String flattenContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isObject()) {
            return content.path("text").asText("");
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : content) {
                String text = item.path("text").asText("");
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append(text.trim());
            }
            return builder.toString();
        }
        return "";
    }

    private String parseErrorMessage(String body, int statusCode) {
        if (!StringUtils.hasText(body)) {
            return "OpenRouter 请求失败，状态码 " + statusCode + "。";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.at("/error/message").asText();
            if (!StringUtils.hasText(message)) {
                message = root.path("message").asText();
            }
            if (StringUtils.hasText(message)) {
                return message;
            }
        } catch (JsonProcessingException ignored) {
        }

        String plainTextBody = body.trim();
        if (StringUtils.hasText(plainTextBody) && !plainTextBody.startsWith("<")) {
            if ("Internal Server Error".equalsIgnoreCase(plainTextBody)) {
                return "OpenRouter 上游内部错误，请稍后重试。";
            }
            return plainTextBody;
        }

        return "OpenRouter 请求失败，状态码 " + statusCode + "。";
    }
}
