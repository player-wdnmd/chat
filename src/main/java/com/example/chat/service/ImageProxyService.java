package com.example.chat.service;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.example.chat.config.ChatAppProperties;
import com.example.chat.config.ImageApiProperties;
import com.example.chat.model.ImageHistoryDetailResponse;
import com.example.chat.model.ImageHistorySummaryResponse;
import com.example.chat.model.ImageToolMetaResponse;
import com.example.chat.model.ImageChatCompletionRequest;
import com.example.chat.model.ImageGenerationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Log4j2
@Service
@RequiredArgsConstructor
public class ImageProxyService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_MAX_UPLOAD_IMAGES = 5;

    private final ChatAppProperties appProperties;
    private final ImageApiProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final ImageRateLimitService imageRateLimitService;
    private final ImageHistoryService imageHistoryService;

    public List<ImageHistorySummaryResponse> listHistory(Long userId) {
        return imageHistoryService.list(userId);
    }

    public ImageHistoryDetailResponse getHistory(Long userId, Long historyId) {
        return imageHistoryService.get(userId, historyId);
    }

    public void deleteHistory(Long userId, Long historyId) {
        imageHistoryService.delete(userId, historyId);
    }

    public int deleteAllHistory(Long userId) {
        return imageHistoryService.deleteAll(userId);
    }

    public ImageToolMetaResponse getMeta() {
        return new ImageToolMetaResponse(
                isConfigured(),
                appProperties.getImage().getCostPerRequest(),
                properties.getMaxUploadSize().toBytes(),
                resolveMaxUploadImages(),
                properties.getAllowedMimeTypes(),
                properties.getRateLimitMaxRequests(),
                properties.getRateLimitWindow().toSeconds()
        );
    }

    public JsonNode generate(Long userId, ImageGenerationRequest request) {
        String model = resolveModel(request.model());
        String prompt = request.prompt().trim();
        return executeBillable(userId, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("prompt", prompt);
            payload.put("response_format", "url");
            return sendJson("/v1/images/generations", payload);
        }, "generate", model, prompt);
    }

    public JsonNode edit(Long userId, String requestedModel, String prompt, MultipartFile image, MultipartFile mask) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("prompt 不能为空。");
        }
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image 文件不能为空。");
        }
        validateUpload(image, "image");
        if (mask != null && !mask.isEmpty()) {
            validateUpload(mask, "mask");
        }
        String model = resolveModel(requestedModel);
        String normalizedPrompt = prompt.trim();

        return executeBillable(userId, () -> {
            try {
                HttpRequest request = createRequest("/v1/images/edits")
                        .form("model", model)
                        .form("prompt", normalizedPrompt)
                        .form("image", image.getBytes(), resolveFilename(image, "image.png"));

                if (mask != null && !mask.isEmpty()) {
                    request.form("mask", mask.getBytes(), resolveFilename(mask, "mask.png"));
                }

                return execute(request);
            } catch (IOException exception) {
                throw new IllegalArgumentException("读取上传图片失败。", exception);
            }
        }, "edit", model, normalizedPrompt);
    }

    public JsonNode chatCompletion(Long userId, ImageChatCompletionRequest request) {
        validateChatCompletionImageCount(request);
        String model = resolveModel(request.model());
        String prompt = extractPrompt(request);
        return executeBillable(userId, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", buildMessages(request));
            return sendJson("/v1/chat/completions", payload);
        }, "compatible", model, prompt);
    }

    private JsonNode executeBillable(Long userId, Supplier<JsonNode> action, String operationType, String model, String prompt) {
        imageRateLimitService.checkAllowed(userId);
        int imageCost = appProperties.getImage().getCostPerRequest();
        int remainingPoints = authService.consumePoints(userId, imageCost);
        try {
            JsonNode response = action.get();
            Long historyId = imageHistoryService.save(
                    userId,
                    operationType,
                    model,
                    prompt,
                    extractImageDataUrl(response),
                    buildResultInfo(response, model, remainingPoints),
                    objectMapper.writeValueAsString(response)
            );
            return appendBillingMeta(response, remainingPoints, historyId);
        } catch (JsonProcessingException exception) {
            authService.refundPoints(userId, imageCost);
            throw new IllegalStateException("图片历史序列化失败。", exception);
        } catch (RuntimeException exception) {
            authService.refundPoints(userId, imageCost);
            throw exception;
        }
    }

    private JsonNode appendBillingMeta(JsonNode response, int remainingPoints, Long historyId) {
        ObjectNode objectNode;
        if (response instanceof ObjectNode existingObject) {
            objectNode = existingObject;
        } else {
            objectNode = objectMapper.createObjectNode();
            objectNode.set("payload", response);
        }
        objectNode.put("remainingPoints", remainingPoints);
        objectNode.put("chargedPoints", appProperties.getImage().getCostPerRequest());
        if (historyId != null) {
            objectNode.put("historyId", historyId);
        }
        return objectNode;
    }

    private List<Map<String, Object>> buildMessages(ImageChatCompletionRequest request) {
        if (request.messages() != null && !request.messages().isEmpty()) {
            return request.messages().stream()
                    .map(message -> buildMessage(message.role(), message.content()))
                    .toList();
        }

        if (StringUtils.hasText(request.role()) && request.content() != null && !request.content().isEmpty()) {
            return List.of(buildMessage(request.role(), request.content()));
        }

        throw new IllegalArgumentException("messages 或 role + content 至少需要提供一组。");
    }

    private Map<String, Object> buildMessage(String role, List<ImageChatCompletionRequest.ContentPart> contentParts) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("message.role 不能为空。");
        }
        if (contentParts == null || contentParts.isEmpty()) {
            throw new IllegalArgumentException("message.content 不能为空。");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role.trim());
        message.put("content", contentParts.stream().map(this::buildContentPart).toList());
        return message;
    }

    private Map<String, Object> buildContentPart(ImageChatCompletionRequest.ContentPart part) {
        if (part == null || !StringUtils.hasText(part.type())) {
            throw new IllegalArgumentException("content.type 不能为空。");
        }

        String type = part.type().trim();
        Map<String, Object> contentPart = new LinkedHashMap<>();
        contentPart.put("type", type);

        if ("text".equals(type)) {
            if (!StringUtils.hasText(part.text())) {
                throw new IllegalArgumentException("text 类型内容必须提供 text。");
            }
            contentPart.put("text", part.text());
            return contentPart;
        }

        if ("image_url".equals(type)) {
            String url = part.imageUrl() == null ? null : part.imageUrl().url();
            if (!StringUtils.hasText(url)) {
                throw new IllegalArgumentException("image_url 类型内容必须提供 imageUrl.url。");
            }
            validateImageUrl(url);
            contentPart.put("image_url", Map.of("url", url));
            return contentPart;
        }

        throw new IllegalArgumentException("暂不支持的 content.type：" + type);
    }

    private JsonNode sendJson(String path, Object payload) {
        try {
            String requestJson = objectMapper.writeValueAsString(payload);
            HttpRequest request = createRequest(path)
                    .contentType(ContentType.JSON.toString())
                    .body(requestJson);
            return execute(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("图片接口请求序列化失败。", exception);
        }
    }

    private JsonNode execute(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            String responseBody = response.body();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                log.warn("图片接口返回错误响应：status={}，body={}", response.getStatus(), responseBody);
                String errorMessage = parseErrorMessage(responseBody, response.getStatus());
                throw new ImageProxyException(errorMessage, resolveErrorStatus(errorMessage, response.getStatus()));
            }
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("图片接口响应解析失败。", exception);
        } catch (HttpException exception) {
            log.error("调用图片接口时发生网络或传输层异常", exception);
            if (exception.getMessage() != null && exception.getMessage().contains("Read timed out")) {
                throw new ImageProxyException(
                        "图片接口在 "
                                + properties.getReadTimeout().toSeconds()
                                + " 秒内未返回，当前更像是上游处理过慢或中转站超时。",
                        HttpStatus.BAD_GATEWAY,
                        exception
                );
            }
            throw new ImageProxyException("调用图片接口失败，请检查上游地址或网络。", exception);
        }
    }

    private HttpRequest createRequest(String path) {
        String apiKey = properties.getApiKey();
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("还没有配置 IMAGE_API_KEY。");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("还没有配置 IMAGE_API_BASE_URL。");
        }

        return HttpRequest.post(joinUrl(baseUrl, path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "*/*")
                .header("User-Agent", "Chat/1.0")
                .setConnectionTimeout((int) properties.getConnectTimeout().toMillis())
                .setReadTimeout((int) properties.getReadTimeout().toMillis());
    }

    private boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey()) && StringUtils.hasText(properties.getBaseUrl());
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel.trim();
        }
        if (StringUtils.hasText(properties.getDefaultModel())) {
            return properties.getDefaultModel().trim();
        }
        throw new IllegalStateException("图片模型未配置。");
    }

    private String resolveFilename(MultipartFile file, String fallback) {
        return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : fallback;
    }

    private void validateUpload(MultipartFile file, String fieldName) {
        long maxUploadBytes = properties.getMaxUploadSize().toBytes();
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException(fieldName + " 文件过大，不能超过 " + maxUploadBytes + " 字节。");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || properties.getAllowedMimeTypes().stream().noneMatch(contentType::equalsIgnoreCase)) {
            throw new IllegalArgumentException(fieldName + " 文件类型不支持，仅允许：" + String.join(", ", properties.getAllowedMimeTypes()));
        }

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (bufferedImage == null) {
                throw new IllegalArgumentException(fieldName + " 不是有效图片文件。");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 " + fieldName + " 文件失败。", exception);
        }
    }

    private void validateImageUrl(String url) {
        if (url.startsWith("data:image/")) {
            int separatorIndex = url.indexOf(';');
            String mimeType = separatorIndex > 5 ? url.substring(5, separatorIndex) : "";
            if (!properties.getAllowedMimeTypes().contains(mimeType)) {
                throw new IllegalArgumentException("data URL 图片类型不支持，仅允许：" + String.join(", ", properties.getAllowedMimeTypes()));
            }
            int base64MarkerIndex = url.indexOf(";base64,");
            if (base64MarkerIndex < 0) {
                throw new IllegalArgumentException("data URL 图片格式不正确。");
            }
            validateDataUrlImage(url.substring(base64MarkerIndex + ";base64,".length()));
            return;
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return;
        }

        throw new IllegalArgumentException("image_url 仅支持 http(s) 或 data:image/... 格式。");
    }

    private void validateChatCompletionImageCount(ImageChatCompletionRequest request) {
        int imageCount = countImageParts(request.content());
        if (request.messages() != null && !request.messages().isEmpty()) {
            imageCount = request.messages().stream()
                    .mapToInt(message -> countImageParts(message.content()))
                    .sum();
        }

        int maxUploadImages = resolveMaxUploadImages();
        if (imageCount > maxUploadImages) {
            throw new IllegalArgumentException("一次最多上传 " + maxUploadImages + " 张图片。");
        }
    }

    private int countImageParts(List<ImageChatCompletionRequest.ContentPart> contentParts) {
        if (contentParts == null || contentParts.isEmpty()) {
            return 0;
        }
        return Math.toIntExact(contentParts.stream()
                .filter(part -> part != null && "image_url".equals(part.type()))
                .count());
    }

    private void validateDataUrlImage(String base64Payload) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("data URL 图片内容不是合法的 Base64。", exception);
        }

        long maxUploadBytes = properties.getMaxUploadSize().toBytes();
        if (bytes.length > maxUploadBytes) {
            throw new IllegalArgumentException("data URL 图片过大，单张不能超过 " + maxUploadBytes + " 字节。");
        }

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage == null) {
                throw new IllegalArgumentException("data URL 不是有效图片文件。");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取 data URL 图片失败。", exception);
        }
    }

    private int resolveMaxUploadImages() {
        Integer maxUploadImages = properties.getMaxUploadImages();
        if (maxUploadImages == null || maxUploadImages < 1) {
            return DEFAULT_MAX_UPLOAD_IMAGES;
        }
        return maxUploadImages;
    }

    private String extractPrompt(ImageChatCompletionRequest request) {
        if (request.messages() != null && !request.messages().isEmpty()) {
            return request.messages().stream()
                    .filter(message -> message.content() != null)
                    .flatMap(message -> message.content().stream())
                    .map(ImageChatCompletionRequest.ContentPart::text)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("图片兼容转换");
        }
        if (request.content() != null && !request.content().isEmpty()) {
            return request.content().stream()
                    .map(ImageChatCompletionRequest.ContentPart::text)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("图片兼容转换");
        }
        return "图片兼容转换";
    }

    private String extractImageDataUrl(JsonNode response) {
        JsonNode firstDataItem = response.path("data").isArray() && !response.path("data").isEmpty()
                ? response.path("data").get(0)
                : null;
        if (firstDataItem != null && firstDataItem.hasNonNull("b64_json")) {
            return "data:image/png;base64," + firstDataItem.path("b64_json").asText();
        }
        if (firstDataItem != null && firstDataItem.hasNonNull("url")) {
            return firstDataItem.path("url").asText();
        }

        String content = response.path("choices").isArray() && !response.path("choices").isEmpty()
                ? response.path("choices").get(0).path("message").path("content").asText("")
                : "";
        String imageUrl = extractImageUrlFromContent(content);
        if (StringUtils.hasText(imageUrl)) {
            return imageUrl;
        }

        throw new IllegalStateException("图片历史保存失败：响应中没有可提取的图片数据。");
    }

    private String extractImageUrlFromContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }

        int markdownStart = content.indexOf("(data:image/");
        if (markdownStart >= 0) {
            int markdownEnd = content.lastIndexOf(')');
            if (markdownEnd > markdownStart) {
                return content.substring(markdownStart + 1, markdownEnd);
            }
        }

        int markdownHttpStart = content.indexOf("(http");
        if (markdownHttpStart >= 0) {
            int markdownHttpEnd = content.indexOf(')', markdownHttpStart);
            if (markdownHttpEnd > markdownHttpStart) {
                return content.substring(markdownHttpStart + 1, markdownHttpEnd);
            }
        }

        String trimmed = content.trim();
        if (trimmed.startsWith("data:image/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "";
    }

    private String buildResultInfo(JsonNode response, String model, int remainingPoints) {
        if (response.hasNonNull("created")) {
            return "模型: " + model
                    + " | 时间: " + formatCreatedValue(response.path("created").asText())
                    + " | 剩余积分: " + formatRemainingPoints(remainingPoints);
        }
        if (response.hasNonNull("id")) {
            return "模型: " + model
                    + " | 请求ID: " + response.path("id").asText()
                    + " | 剩余积分: " + formatRemainingPoints(remainingPoints);
        }
        return "模型: " + model + " | 剩余积分: " + formatRemainingPoints(remainingPoints);
    }

    private String formatCreatedValue(String rawValue) {
        try {
            long numericValue = Long.parseLong(rawValue);
            long epochMillis = numericValue < 1_000_000_000_000L ? numericValue * 1000 : numericValue;
            return DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
        } catch (NumberFormatException exception) {
            return rawValue;
        }
    }

    private String formatRemainingPoints(int remainingPoints) {
        Integer unlimitedPoints = appProperties.getLimits().getUnlimitedPoints();
        return unlimitedPoints != null && remainingPoints >= unlimitedPoints ? "无限" : String.valueOf(remainingPoints);
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBaseUrl + normalizedPath;
    }

    private String parseErrorMessage(String body, int statusCode) {
        if (!StringUtils.hasText(body)) {
            return "图片接口请求失败，状态码 " + statusCode + "。";
        }

        List<String> candidates = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            candidates.add(root.at("/error/message").asText());
            candidates.add(root.path("message").asText());
            candidates.add(root.path("detail").asText());
            for (String candidate : candidates) {
                if (StringUtils.hasText(candidate)) {
                    if (isPolicyViolationMessage(candidate)) {
                        return "当前提示词或生成结果被上游安全策略拦截，请调整描述后重试。";
                    }
                    return candidate;
                }
            }
        } catch (JsonProcessingException ignored) {
        }

        if (isPolicyViolationMessage(body)) {
            return "当前提示词或生成结果被上游安全策略拦截，请调整描述后重试。";
        }
        return "图片接口请求失败，状态码 " + statusCode + "。";
    }

    private HttpStatus resolveErrorStatus(String message, int upstreamStatusCode) {
        if (isPolicyViolationMessage(message)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (upstreamStatusCode == 429) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private boolean isPolicyViolationMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("violated our relevant policies")
                || normalized.contains("violated")
                || normalized.contains("policy")
                || normalized.contains("content_filter");
    }
}
