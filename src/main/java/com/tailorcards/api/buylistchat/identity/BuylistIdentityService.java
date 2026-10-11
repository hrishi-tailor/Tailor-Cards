package com.tailorcards.api.buylistchat.identity;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.email.EmailSender;
import com.tailorcards.api.buylistchat.entity.BuylistChatSession;
import com.tailorcards.api.buylistchat.entity.BuylistOtpCode;
import com.tailorcards.api.buylistchat.repository.BuylistChatSessionRepository;
import com.tailorcards.api.buylistchat.repository.BuylistOtpCodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email one-time-code sign-in: 6-digit codes stored as HMAC hashes, 10-minute expiry, 5 attempts,
 * resend cooldown and hourly cap. A verified code yields an opaque session token (stored hashed).
 */
@Slf4j
@Service
public class BuylistIdentityService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,63}$");

    private final BuylistOtpCodeRepository codeRepository;
    private final BuylistChatSessionRepository sessionRepository;
    private final EmailSender emailSender;
    private final TurnstileVerifier turnstileVerifier;
    private final BuylistChatProperties.Otp config;
    private final byte[] pepper;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BuylistIdentityService(BuylistOtpCodeRepository codeRepository, BuylistChatSessionRepository sessionRepository,
                                  EmailSender emailSender, TurnstileVerifier turnstileVerifier,
                                  BuylistChatProperties properties) {
        this(codeRepository, sessionRepository, emailSender, turnstileVerifier, properties, Clock.systemUTC());
    }

    BuylistIdentityService(BuylistOtpCodeRepository codeRepository, BuylistChatSessionRepository sessionRepository,
                           EmailSender emailSender, TurnstileVerifier turnstileVerifier,
                           BuylistChatProperties properties, Clock clock) {
        this.codeRepository = codeRepository;
        this.sessionRepository = sessionRepository;
        this.emailSender = emailSender;
        this.turnstileVerifier = turnstileVerifier;
        this.config = properties.getOtp();
        this.clock = clock;
        String configured = config.getPepper();
        if (configured != null && !configured.isBlank()) {
            this.pepper = configured.getBytes(StandardCharsets.UTF_8);
        } else {
            this.pepper = new byte[32];
            random.nextBytes(this.pepper); // codes and their hashes only live for minutes
        }
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    @Transactional
    public void requestCode(String rawEmail, String turnstileToken, String ip) {
        String email = normalizeEmail(rawEmail);
        if (!turnstileVerifier.verify(turnstileToken, ip)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please complete the verification check and try again.");
        }
        Instant now = clock.instant();
        codeRepository.findTopByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            if (last.getCreatedAt().plusSeconds(config.getResendCooldownSeconds()).isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "A code was just sent. Please wait a minute before asking for another.");
            }
        });
        if (codeRepository.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1))) >= config.getMaxCodesPerEmailPerHour()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested. Please try again in an hour.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        codeRepository.save(BuylistOtpCode.builder()
                .email(email)
                .codeHash(hashCode(email, code))
                .attempts(0)
                .requestIp(ip)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(config.getCodeTtlMinutes())))
                .build());
        emailSender.send(email, "Your Tailor Cards sign-in code",
                "Your sign-in code is " + code + ".\n\nIt expires in " + config.getCodeTtlMinutes()
                        + " minutes. If you didn't ask for it, you can ignore this email.");
    }

    /** Verifies the latest code for the email and returns a new session token. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public IssuedSession verifyCode(String rawEmail, String code, String ip) {
        String email = normalizeEmail(rawEmail);
        Instant now = clock.instant();
        BuylistOtpCode latest = codeRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .filter(c -> c.getConsumedAt() == null)
                .orElseThrow(() -> invalid("That code is not valid. Request a new one."));
        if (latest.getExpiresAt().isBefore(now)) {
            throw invalid("That code has expired. Request a new one.");
        }
        if (latest.getAttempts() >= config.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Request a new code.");
        }
        latest.setAttempts(latest.getAttempts() + 1);
        boolean matches = code != null && code.trim().matches("\\d{6}")
                && MessageDigest.isEqual(latest.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hashCode(email, code.trim()).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            codeRepository.save(latest);
            int left = config.getMaxAttempts() - latest.getAttempts();
            throw invalid(left > 0 ? "That code is not right. " + left + " attempt(s) left." : "Too many attempts. Request a new code.");
        }
        latest.setConsumedAt(now);
        codeRepository.save(latest);

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expires = now.plus(Duration.ofHours(config.getSessionTtlHours()));
        BuylistChatSession session = sessionRepository.save(BuylistChatSession.builder()
                .tokenHash(sha256(token)).email(email).createdIp(ip).createdAt(now).expiresAt(expires).build());
        return new IssuedSession(token, email, expires, session.getId());
    }

    /** Prefix of the identity given to anonymous sessions when email verification is off. */
    public static final String GUEST_PREFIX = "guest:";

    public static boolean isGuest(String identity) {
        return identity != null && identity.startsWith(GUEST_PREFIX);
    }

    /** Anonymous session (email verification turned off): the identity only owns its own drafts. */
    @Transactional
    public IssuedSession createGuestSession(String ip) {
        Instant now = clock.instant();
        String identity = GUEST_PREFIX + java.util.UUID.randomUUID();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expires = now.plus(Duration.ofHours(config.getSessionTtlHours()));
        BuylistChatSession session = sessionRepository.save(BuylistChatSession.builder()
                .tokenHash(sha256(token)).email(identity).createdIp(ip).createdAt(now).expiresAt(expires).build());
        return new IssuedSession(token, null, expires, session.getId());
    }

    /** Resolves a session token or throws 401. */
    @Transactional(readOnly = true)
    public BuylistChatSession authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please verify your email to continue.");
        }
        return sessionRepository.findByTokenHash(sha256(token.trim()))
                .filter(s -> s.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Your session expired. Please verify your email again."));
    }

    public record IssuedSession(String token, String email, Instant expiresAt, Long sessionId) {}

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    String hashCode(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
