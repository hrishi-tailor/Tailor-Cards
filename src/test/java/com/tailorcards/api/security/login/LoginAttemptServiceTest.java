package com.tailorcards.api.security.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Login attempts: lockout and IP blocking")
class LoginAttemptServiceTest {

    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService attempts = new LoginAttemptService(new LoginAttemptService.Properties(), clock);

    @Test
    @DisplayName("Five failures lock the account for 15 minutes; it unlocks afterwards")
    void accountLockout() {
        for (int i = 0; i < 4; i++) attempts.failure("admin", "1.1.1." + i);
        assertThat(attempts.blockedFor("admin", "9.9.9.9")).isEmpty();
        attempts.failure("Admin", "1.1.1.9"); // usernames are case-insensitive
        assertThat(attempts.blockedFor("admin", "9.9.9.9")).hasValue(Duration.ofMinutes(15));
        assertThat(attempts.blockedFor("someone-else", "9.9.9.9")).isEmpty();
        clock.now = clock.now.plus(Duration.ofMinutes(14));
        assertThat(attempts.blockedFor("admin", "9.9.9.9")).hasValue(Duration.ofMinutes(1));
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThat(attempts.blockedFor("admin", "9.9.9.9")).isEmpty();
    }

    @Test
    @DisplayName("Failures older than the 15-minute window don't count")
    void slidingWindow() {
        for (int i = 0; i < 4; i++) attempts.failure("admin", null);
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        attempts.failure("admin", null);
        assertThat(attempts.blockedFor("admin", null)).isEmpty();
    }

    @Test
    @DisplayName("A complete sign-in clears the account's failures but not the IP's")
    void successClearsAccountOnly() {
        for (int i = 0; i < 4; i++) attempts.failure("admin", "2.2.2.2");
        attempts.success("admin");
        attempts.failure("admin", "2.2.2.2");
        assertThat(attempts.blockedFor("admin", "3.3.3.3")).isEmpty();
        for (int i = 0; i < 15; i++) attempts.failure("user" + i, "2.2.2.2"); // 20 from this IP in total
        assertThat(attempts.blockedFor("anyone", "2.2.2.2")).isPresent();
        assertThat(attempts.blockedFor("anyone", "3.3.3.3")).isEmpty();
    }
}
