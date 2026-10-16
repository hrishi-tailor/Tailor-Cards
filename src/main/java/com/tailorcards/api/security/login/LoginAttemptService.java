package com.tailorcards.api.security.login;

import com.tailorcards.api.security.LogSafe;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Failed admin sign-ins (wrong password or wrong two-factor code), per account and per IP. Too many in the window
 * locks the account, or blocks the IP, for a while; blocked requests are refused before the password is checked.
 * Only a complete sign-in clears an account's failures. Kept in memory: a restart clears it.
 */
@Slf4j
@Service
public class LoginAttemptService {

    @Getter
    @Setter
    @Component
    @ConfigurationProperties(prefix = "app.security.login")
    public static class Properties {
        /** Failures on one account within the window before it's locked. */
        private int maxFailures = 5;
        /** Failures from one IP (any account) within the window before it's blocked. */
        private int maxFailuresPerIp = 20;
        private int windowMinutes = 15;
        private int lockoutMinutes = 15;
    }

    private static final int MAX_TRACKED = 10_000;

    private static final class Record {
        final Deque<Instant> failures = new ArrayDeque<>();
        Instant lockedUntil;
    }

    private final Properties properties;
    private final Clock clock;
    private final Map<String, Record> accounts = new ConcurrentHashMap<>();
    private final Map<String, Record> ips = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(Properties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptService(Properties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** How long until this account or IP may try again; empty when allowed. */
    public Optional<Duration> blockedFor(String username, String ip) {
        Instant now = clock.instant();
        Duration wait = Duration.ZERO;
        for (Record r : new Record[]{accounts.get(key(username)), ip == null ? null : ips.get(ip)}) {
            if (r != null) {
                synchronized (r) {
                    if (r.lockedUntil != null && r.lockedUntil.isAfter(now)) {
                        Duration left = Duration.between(now, r.lockedUntil);
                        if (left.compareTo(wait) > 0) wait = left;
                    }
                }
            }
        }
        return wait.isZero() ? Optional.empty() : Optional.of(wait);
    }

    public void failure(String username, String ip) {
        if (username != null && !username.isBlank()
                && record(accounts, key(username), properties.getMaxFailures())) {
            log.warn("Admin sign-in locked for '{}' for {} min after {} failures", LogSafe.of(username),
                    properties.getLockoutMinutes(), properties.getMaxFailures());
        }
        if (ip != null && record(ips, ip, properties.getMaxFailuresPerIp())) {
            log.warn("Admin sign-in blocked for an IP for {} min after {} failures", properties.getLockoutMinutes(),
                    properties.getMaxFailuresPerIp());
        }
    }

    /** A complete sign-in: clears the account's failures (not the IP's). */
    public void success(String username) {
        if (username != null) {
            accounts.remove(key(username));
        }
    }

    /** Records a failure; true when it just caused a lock. */
    private boolean record(Map<String, Record> map, String key, int max) {
        if (map.size() > MAX_TRACKED) {
            prune(map);
        }
        Record r = map.computeIfAbsent(key, k -> new Record());
        Instant now = clock.instant();
        synchronized (r) {
            Instant windowStart = now.minus(Duration.ofMinutes(properties.getWindowMinutes()));
            while (!r.failures.isEmpty() && r.failures.peekFirst().isBefore(windowStart)) {
                r.failures.pollFirst();
            }
            r.failures.addLast(now);
            if (r.failures.size() >= max && (r.lockedUntil == null || !r.lockedUntil.isAfter(now))) {
                r.lockedUntil = now.plus(Duration.ofMinutes(properties.getLockoutMinutes()));
                r.failures.clear();
                return true;
            }
            return false;
        }
    }

    private void prune(Map<String, Record> map) {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(properties.getWindowMinutes() + properties.getLockoutMinutes()));
        map.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Instant last = e.getValue().failures.peekLast();
                return (e.getValue().lockedUntil == null || e.getValue().lockedUntil.isBefore(clock.instant()))
                        && (last == null || last.isBefore(cutoff));
            }
        });
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
