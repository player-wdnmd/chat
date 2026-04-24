package com.example.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.chat.config.ChatAppProperties;
import com.example.chat.entity.ChatUserImageHistory;
import com.example.chat.mapper.ChatUserImageHistoryMapper;
import com.example.chat.model.ImageHistoryDetailResponse;
import com.example.chat.model.ImageHistorySummaryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Log4j2
@Service
@RequiredArgsConstructor
public class ImageHistoryService {

    private final ChatAppProperties properties;
    private final ChatUserImageHistoryMapper imageHistoryMapper;

    @Transactional
    public Long save(
            Long userId,
            String operationType,
            String model,
            String prompt,
            String imageDataUrl,
            String resultInfo,
            String responseJson
    ) {
        ChatUserImageHistory history = new ChatUserImageHistory();
        history.setUserId(userId);
        history.setOperationType(operationType);
        history.setModel(model);
        history.setPrompt(prompt);
        history.setImageDataUrl(imageDataUrl);
        history.setResultInfo(resultInfo);
        history.setResponseJson(responseJson);
        imageHistoryMapper.insert(history);
        log.info("图片历史保存成功：userId={}，historyId={}，operationType={}", userId, history.getId(), operationType);
        return history.getId();
    }

    public List<ImageHistorySummaryResponse> list(Long userId) {
        return imageHistoryMapper.selectList(
                        Wrappers.<ChatUserImageHistory>lambdaQuery()
                                .eq(ChatUserImageHistory::getUserId, userId)
                                .orderByDesc(ChatUserImageHistory::getId)
                ).stream()
                .limit(resolveHistoryLimit())
                .map(this::toSummary)
                .toList();
    }

    public ImageHistoryDetailResponse get(Long userId, Long historyId) {
        ChatUserImageHistory history = imageHistoryMapper.selectById(historyId);
        if (history == null || !userId.equals(history.getUserId())) {
            throw new IllegalArgumentException("图片历史不存在。");
        }
        return new ImageHistoryDetailResponse(
                history.getId(),
                history.getOperationType(),
                history.getModel(),
                history.getPrompt(),
                history.getImageDataUrl(),
                history.getResultInfo(),
                StringUtils.hasText(history.getResponseJson()) ? history.getResponseJson() : "{}",
                history.getCreatedAt()
        );
    }

    @Transactional
    public void delete(Long userId, Long historyId) {
        int deletedRows = imageHistoryMapper.delete(
                Wrappers.<ChatUserImageHistory>lambdaQuery()
                        .eq(ChatUserImageHistory::getId, historyId)
                        .eq(ChatUserImageHistory::getUserId, userId)
        );
        if (deletedRows == 0) {
            throw new IllegalArgumentException("要删除的图片历史不存在。");
        }
        log.info("图片历史删除成功：userId={}，historyId={}", userId, historyId);
    }

    @Transactional
    public int deleteAll(Long userId) {
        int deletedRows = imageHistoryMapper.delete(
                Wrappers.<ChatUserImageHistory>lambdaQuery()
                        .eq(ChatUserImageHistory::getUserId, userId)
        );
        log.info("图片历史已清空：userId={}，deletedRows={}", userId, deletedRows);
        return deletedRows;
    }

    private ImageHistorySummaryResponse toSummary(ChatUserImageHistory history) {
        return new ImageHistorySummaryResponse(
                history.getId(),
                history.getOperationType(),
                history.getModel(),
                history.getPrompt(),
                history.getImageDataUrl(),
                history.getResultInfo(),
                history.getCreatedAt()
        );
    }

    private long resolveHistoryLimit() {
        Integer configuredLimit = properties.getImage().getHistoryLimit();
        if (configuredLimit == null) {
            return 20L;
        }
        return Math.max(1, Math.min(configuredLimit, 100));
    }
}
