package com.tailorcards.api.security.twofactor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Admin second factor: TOTP enrolment (QR code), verification with replay protection, single-use backup codes, and
 * the second-factor session every admin request must carry (X-Admin-Session).
 */
@Slf4j
@Service
public class TwoFactorService {

    public record Status(boolean required, boolean enrolled) {}

    public record Enrolment(String secret, String otpauthUri) {}

    public record Session(String token, Instant expiresAt) {}

    public record Verified(Session session, List<String> backupCodes, int backupCodesLeft) {}

    /** Readable alphabet without 0/O, 1/I/L. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final AdminTwoFactorRepository enrolments;
    private final AdminBackupCodeRepository backupCodes;
    private final AdminSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final SecretCipher cipher;

    @Autowired
    public TwoFactorService(AdminTwoFactorRepository enrolments, AdminBackupCodeRepository backupCodes,
                            AdminSessionRepository sessions, PasswordEncoder passwordEncoder,
                            TwoFactorProperties properties) {
        this(enrolments, backupCodes, sessions, passwordEncoder, properties, Clock.systemUTC());
    }

    TwoFactorService(AdminTwoFactorRepository enrolments, AdminBackupCodeRepository backupCodes,
                     AdminSessionRepository sessions, PasswordEncoder passwordEncoder,
                     TwoFactorProperties properties, Clock clock) {
        this.enrolments = enrolments;
        this.backupCodes = backupCodes;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        if (properties.isEnabled() && (properties.getEncryptionKey() == null || properties.getEncryptionKey().isBlank())) {
            throw new IllegalStateException("CRITICAL SECURITY CONFIGURATION ERROR: admin two-factor login is on but "
                    + "TOTP_ENCRYPTION_KEY is not set. Generate one with: openssl rand -base64 32");
        }
        this.cipher = properties.getEncryptionKey() == null || properties.getEncryptionKey().isBlank()
                ? null : new SecretCipher(properties.getEncryptionKey());
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Status status(String username) {
        return new Status(properties.isEnabled(), enrolments.findById(username).map(AdminTwoFactor::isEnabled).orElse(false));
    }

    /** Starts (or restarts) enrolment: a new secret, pending until a code from it is confirmed. */
    @Transactional
    public Enrolment startEnrolment(String username) {
        AdminTwoFactor row = enrolments.findById(username).orElseGet(() -> {
            AdminTwoFactor fresh = new AdminTwoFactor();
            fresh.setUsername(username);
            return fresh;
        });
        if (row.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-factor login is already set up for this account.");
        }
        byte[] secret = Totp.newSecret(random);
        row.setPendingSecretCiphertext(cipher().encrypt(secret));
        row.setUpdatedAt(clock.instant());
        enrolments.save(row);
        return new Enrolment(Totp.base32(secret), Totp.otpauthUri(properties.getIssuer(), username, secret));
    }

    /** Confirms enrolment with a code from the new secret; returns backup codes (shown once) and a session. */
    @Transactional
    public Verified confirmEnrolment(String username, String code, String ip) {
        AdminTwoFactor row = enrolments.findById(username)
                .filter(r -> r.getPendingSecretCiphertext() != null && !r.isEnabled())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Start two-factor setup first."));
        byte[] secret = cipher().decrypt(row.getPendingSecretCiphertext());
        long step = Totp.verify(secret, code, clock.instant().getEpochSecond(), row.getLastUsedStep());
        if (step < 0) {
            throw new BadCodeException();
        }
        row.setSecretCiphertext(row.getPendingSecretCiphertext());
        row.setPendingSecretCiphertext(null);
        row.setEnabled(true);
        row.setLastUsedStep(step);
        row.setEnrolledAt(clock.instant());
        row.setUpdatedAt(clock.instant());
        enrolments.save(row);
        List<String> codes = newBackupCodes(username);
        log.info("Admin two-factor enrolled for '{}'", username);
        return new Verified(newSession(username, ip), codes, codes.size());
    }

    /** A TOTP code or an unused backup code; returns a session. */
    @Transactional
    public Verified verify(String username, String code, String ip) {
        AdminTwoFactor row = enrolments.findById(username).filter(AdminTwoFactor::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Set up two-factor login first."));
        long step = Totp.verify(cipher().decrypt(row.getSecretCiphertext()), code, clock.instant().getEpochSecond(), row.getLastUsedStep());
        if (step >= 0) {
            row.setLastUsedStep(step);
            row.setUpdatedAt(clock.instant());
            enrolments.save(row);
            return new Verified(newSession(username, ip), List.of(), backupCodes.findByUsernameAndUsedAtIsNull(username).size());
        }
        Optional<AdminBackupCode> backup = matchingBackupCode(username, code);
        if (backup.isPresent()) {
            backup.get().setUsedAt(clock.instant());
            backupCodes.save(backup.get());
            int left = backupCodes.findByUsernameAndUsedAtIsNull(username).size();
            log.warn("Admin '{}' signed in with a backup code ({} left)", username, left);
            return new Verified(newSession(username, ip), List.of(), left);
        }
        throw new BadCodeException();
    }

    /** New backup codes (old ones stop working); needs a current TOTP code. */
    @Transactional
    public List<String> regenerateBackupCodes(String username, String totpCode) {
        AdminTwoFactor row = enrolments.findById(username).filter(AdminTwoFactor::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Set up two-factor login first."));
        long step = Totp.verify(cipher().decrypt(row.getSecretCiphertext()), totpCode, clock.instant().getEpochSecond(), row.getLastUsedStep());
        if (step < 0) {
            throw new BadCodeException();
        }
        row.setLastUsedStep(step);
        enrolments.save(row);
        return newBackupCodes(username);
    }

    /** True when the token is a live session of this user. */
    public boolean validSession(String username, String token) {
        if (token == null || token.isBlank() || username == null) {
            return false;
        }
        return sessions.findById(hash(token))
                .filter(s -> s.getUsername().equals(username) && s.getExpiresAt().isAfter(clock.instant()))
                .isPresent();
    }

    @Transactional
    public void signOut(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteById(hash(token));
        }
    }

    private Session newSession(String username, String ip) {
        sessions.deleteExpired(clock.instant());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AdminSession s = new AdminSession();
        s.setTokenHash(hash(token));
        s.setUsername(username);
        s.setCreatedIp(ip == null ? null : ip.substring(0, Math.min(64, ip.length())));
        s.setCreatedAt(clock.instant());
        s.setExpiresAt(clock.instant().plus(Duration.ofHours(properties.getSessionHours())));
        sessions.save(s);
        return new Session(token, s.getExpiresAt());
    }

    private List<String> newBackupCodes(String username) {
        backupCodes.deleteByUsername(username);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < properties.getBackupCodes(); i++) {
            StringBuilder code = new StringBuilder();
            for (int c = 0; c < 10; c++) {
                if (c == 5) code.append('-');
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            AdminBackupCode row = new AdminBackupCode();
            row.setUsername(username);
            row.setCodeHash(passwordEncoder.encode(normalizeBackup(code.toString())));
            row.setCreatedAt(clock.instant());
            backupCodes.save(row);
            codes.add(code.toString());
        }
        return codes;
    }

    private Optional<AdminBackupCode> matchingBackupCode(String username, String code) {
        String normalized = normalizeBackup(code);
        if (normalized.length() != 10) {
            return Optional.empty();
        }
        return backupCodes.findByUsernameAndUsedAtIsNull(username).stream()
                .filter(b -> passwordEncoder.matches(normalized, b.getCodeHash()))
                .findFirst();
    }

    private static String normalizeBackup(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    private SecretCipher cipher() {
        if (cipher == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Two-factor login isn't configured (TOTP_ENCRYPTION_KEY).");
        }
        return cipher;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A wrong TOTP or backup code (counted towards the login lockout). */
    public static class BadCodeException extends ResponseStatusException {
        public BadCodeException() {
            super(HttpStatus.UNAUTHORIZED, "That code isn't right. Check the time on your phone and try the newest code.");
        }
    }
}
