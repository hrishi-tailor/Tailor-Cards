package com.tailorcards.api.security.login;

import com.tailorcards.api.security.ClientAddress;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Runs before the password check: a request with admin credentials for a locked account, or from a blocked IP, is
 * refused with 429 and Retry-After without checking the password, so guessing can't continue during a lockout.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginAttemptService attempts;
    private final int trustedHops;

    public LoginThrottleFilter(LoginAttemptService attempts, int trustedHops) {
        this.attempts = attempts;
        this.trustedHops = trustedHops;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = basicUsername(request);
        if (username != null) {
            Optional<Duration> blocked = attempts.blockedFor(username, ClientAddress.of(request, trustedHops));
            if (blocked.isPresent()) {
                long seconds = Math.max(1, blocked.get().toSeconds());
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(seconds));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"too_many_attempts\",\"retryAfterSeconds\":" + seconds
                        + ",\"message\":\"Too many failed sign-in attempts. Try again in " + Math.max(1, (seconds + 59) / 60)
                        + " minute(s).\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The username from an HTTP Basic header, or null. */
    static String basicUsername(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon > 0 ? decoded.substring(0, colon) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
