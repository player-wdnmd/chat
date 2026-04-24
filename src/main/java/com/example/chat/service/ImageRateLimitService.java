package com.example.chat.service;

import com.example.chat.config.ImageApiProperties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageRateLimitService {

    private final ImageApiProperties properties;
    private final ConcurrentHashMap<Long, Deque<Long>> userBuckets = new ConcurrentHashMap<>();

    public void checkAllowed(Long userId) {
        int maxRequests = properties.getRateLimitMaxRequests();
        long windowMillis = properties.getRateLimitWindow().toMillis();
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        Deque<Long> bucket = userBuckets.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < cutoff) {
                bucket.pollFirst();
            }
            if (bucket.size() >= maxRequests) {
                throw new RateLimitExceededException(
                        "图片请求过于频繁，请稍后再试。当前限制为 "
                                + maxRequests
                                + " 次 / "
                                + Math.max(1, windowMillis / 1000)
                                + " 秒。"
                );
            }
            bucket.addLast(now);
        }
    }
}
