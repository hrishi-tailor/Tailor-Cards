package com.tailorcards.api.trade.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LlmRateLimiter Tests")
class LlmRateLimiterTest {

    @Test
    @DisplayName("Allows requests within configured rate limit")
    void testAllowsRequestsWithinLimit() {
        LlmRateLimiter limiter = new LlmRateLimiter(5);

        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> limiter.checkRateLimit("session-1"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Throws 429 Too Many Requests when rate limit is exceeded")
    void testThrowsWhenRateLimitExceeded() {
        LlmRateLimiter limiter = new LlmRateLimiter(3);

        limiter.checkRateLimit("session-2");
        limiter.checkRateLimit("session-2");
        limiter.checkRateLimit("session-2");

        assertThatThrownBy(() -> limiter.checkRateLimit("session-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }

    @Test
    @DisplayName("Different keys have independent buckets")
    void testIndependentBucketsForDifferentKeys() {
        LlmRateLimiter limiter = new LlmRateLimiter(2);

        limiter.checkRateLimit("user-a");
        limiter.checkRateLimit("user-a");

        assertThatCode(() -> limiter.checkRateLimit("user-b"))
                .doesNotThrowAnyException();
    }
}
