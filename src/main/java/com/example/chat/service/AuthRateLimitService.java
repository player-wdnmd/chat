package com.example.chat.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimitService {

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public void checkAllowed(String bucketKey, int maxRequests, Duration window, String message) {
        long windowMillis = window.toMillis();
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        Deque<Long> bucket = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < cutoff) {
                bucket.pollFirst();
            }
            if (bucket.size() >= maxRequests) {
                throw new RateLimitExceededException(message);
            }
            bucket.addLast(now);
        }
    }
}
