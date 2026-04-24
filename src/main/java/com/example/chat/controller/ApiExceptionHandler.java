package com.example.chat.controller;

import com.example.chat.service.AccountAlreadyExistsException;
import com.example.chat.service.AuthenticationFailedException;
import com.example.chat.service.ImageProxyException;
import com.example.chat.service.InsufficientPointsException;
import com.example.chat.service.OpenRouterException;
import com.example.chat.service.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Log4j2
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException exception) {
        log.warn("请求参数校验失败：{}", exception.getMessage());
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(" "));
        return new ApiError(message.isBlank() ? "请求参数格式不正确。" : message, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraint(ConstraintViolationException exception) {
        log.warn("约束校验失败：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(Exception exception) {
        log.warn("收到非法请求：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiError handleMultipart(Exception exception) {
        log.warn("上传文件过大或 multipart 解析失败：{}", exception.getMessage());
        return new ApiError("上传文件过大或格式不正确，请压缩图片后重试。", Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleServiceUnavailable(IllegalStateException exception) {
        log.warn("服务当前不可用：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleAccountExists(AccountAlreadyExistsException exception) {
        log.warn("注册失败，账号名冲突：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleAuthFailed(AuthenticationFailedException exception) {
        log.warn("认证失败：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InsufficientPointsException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleInsufficientPoints(InsufficientPointsException exception) {
        log.warn("积分不足：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler(OpenRouterException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiError handleOpenRouter(OpenRouterException exception) {
        log.error("调用 OpenRouter 失败：{}", exception.getMessage(), exception);
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ImageProxyException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiError handleImageProxy(ImageProxyException exception) {
        log.error("调用图片接口失败：{}", exception.getMessage(), exception);
        return new ApiError(exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiError handleRateLimit(RateLimitExceededException exception) {
        log.warn("触发限流：{}", exception.getMessage());
        return new ApiError(exception.getMessage(), Map.of());
    }

    private String formatFieldError(FieldError error) {
        return error.getDefaultMessage();
    }
}
