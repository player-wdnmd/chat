package com.example.chat.controller;

import com.example.chat.model.AuthenticatedUser;
import com.example.chat.model.ImageHistoryDetailResponse;
import com.example.chat.model.ImageHistorySummaryResponse;
import com.example.chat.model.ImageChatCompletionRequest;
import com.example.chat.model.ImageGenerationRequest;
import com.example.chat.model.ImageToolMetaResponse;
import com.example.chat.service.AuthService;
import com.example.chat.service.ImageProxyService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Log4j2
@Validated
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final AuthService authService;
    private final ImageProxyService imageProxyService;

    @GetMapping("/meta")
    public ImageToolMetaResponse meta(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("读取图片工具元信息：userId={}", user.id());
        return imageProxyService.getMeta();
    }

    @GetMapping("/history")
    public List<ImageHistorySummaryResponse> history(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("读取图片历史列表：userId={}", user.id());
        return imageProxyService.listHistory(user.id());
    }

    @GetMapping("/history/{historyId}")
    public ImageHistoryDetailResponse historyDetail(HttpServletRequest request, @PathVariable Long historyId) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("读取图片历史详情：userId={}，historyId={}", user.id(), historyId);
        return imageProxyService.getHistory(user.id(), historyId);
    }

    @DeleteMapping("/history/{historyId}")
    public void deleteHistory(HttpServletRequest request, @PathVariable Long historyId) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("删除图片历史：userId={}，historyId={}", user.id(), historyId);
        imageProxyService.deleteHistory(user.id(), historyId);
    }

    @DeleteMapping("/history")
    public void deleteAllHistory(HttpServletRequest request) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("清空图片历史：userId={}", user.id());
        imageProxyService.deleteAllHistory(user.id());
    }

    @PostMapping("/generations")
    public JsonNode generate(HttpServletRequest request, @Valid @RequestBody ImageGenerationRequest generationRequest) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到文生图请求：userId={}，model={}", user.id(), generationRequest.model());
        return imageProxyService.generate(user.id(), generationRequest);
    }

    @PostMapping(value = "/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JsonNode edit(
            HttpServletRequest request,
            @RequestParam(required = false) String model,
            @RequestParam @NotBlank(message = "prompt 不能为空") @Size(max = 4000, message = "prompt 不能超过 4000 个字符") String prompt,
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "mask", required = false) MultipartFile mask
    ) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到图片编辑请求：userId={}，model={}，imageName={}", user.id(), model, image.getOriginalFilename());
        return imageProxyService.edit(user.id(), model, prompt, image, mask);
    }

    @PostMapping("/chat/completions")
    public JsonNode chatCompletion(
            HttpServletRequest request,
            @Valid @RequestBody ImageChatCompletionRequest chatCompletionRequest
    ) {
        AuthenticatedUser user = authService.requireAuthenticatedUser(request);
        log.info("收到图片兼容 completions 请求：userId={}，model={}", user.id(), chatCompletionRequest.model());
        return imageProxyService.chatCompletion(user.id(), chatCompletionRequest);
    }
}
