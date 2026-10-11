package com.tailorcards.api.trade.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LlmRateLimiter {

    private final int maxRequestsPerMinute;
    private final Map<String, Deque<Instant>> requestBuckets = new ConcurrentHashMap<>();

    public LlmRateLimiter(
            @Value("${app.trade-assistant.rate-limit-per-minute:20}") int maxRequestsPerMinute
    ) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Checks rate limit for a key (e.g. conversationId or client IP).
     * Throws 429 Too Many Requests if rate limit is exceeded.
     */
    public void checkRateLimit(String key) {
        checkRateLimit(key, maxRequestsPerMinute, Duration.ofMinutes(1),
                "Rate limit exceeded: Please wait a moment before sending more messages.");
    }

    /**
     * Sliding-window rate limit with a caller-supplied limit and window. Callers sharing this
     * limiter should namespace their keys (e.g. "listing:admin") so windows do not mix.
     */
    public void checkRateLimit(String key, int maxRequests, Duration window, String message) {
        if (key == null || key.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minus(window);

        Deque<Instant> bucket = requestBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (bucket) {
            // Evict timestamps older than the window
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(windowStart)) {
                bucket.pollFirst();
            }

            if (bucket.size() >= maxRequests) {
                log.warn("Rate limit exceeded for key '{}' ({} requests in last {}s)", key, bucket.size(), window.toSeconds());
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
            }

            bucket.addLast(now);
        }
    }
}
