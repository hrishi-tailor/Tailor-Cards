package com.tailorcards.api.security.twofactor;

import com.tailorcards.api.security.ClientAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Admin second factor. Every call here needs the admin password (HTTP Basic); these are the only admin endpoints
 * that work without a second-factor session.
 */
@RestController
@RequestMapping("/api/auth/2fa")
@Tag(name = "Admin two-factor login")
public class TwoFactorController {

    public record CodeRequest(String code) {}

    public record EnrolmentResponse(String secret, String otpauthUri) {}

    public record SessionResponse(String sessionToken, Instant expiresAt, List<String> backupCodes, int backupCodesLeft) {}

    public record BackupCodesResponse(List<String> backupCodes) {}

    private final TwoFactorService twoFactor;
    private final int trustedHops;

    public TwoFactorController(TwoFactorService twoFactor,
                               @Value("${app.security.trusted-proxy-hops:1}") int trustedHops) {
        this.twoFactor = twoFactor;
        this.trustedHops = trustedHops;
    }

    @GetMapping("/status")
    @Operation(summary = "Whether two-factor login is required and set up for this admin")
    public TwoFactorService.Status status(Authentication auth) {
        return twoFactor.status(auth.getName());
    }

    @PostMapping("/enrol")
    @Operation(summary = "Start setup: a new secret and otpauth:// URI for the QR code (pending until confirmed)")
    public EnrolmentResponse enrol(Authentication auth) {
        TwoFactorService.Enrolment e = twoFactor.startEnrolment(auth.getName());
        return new EnrolmentResponse(e.secret(), e.otpauthUri());
    }

    @PostMapping("/enrol/confirm")
    @Operation(summary = "Finish setup with a code from the app; returns backup codes (shown once) and a session")
    public SessionResponse confirm(Authentication auth, @RequestBody CodeRequest request, HttpServletRequest http) {
        return response(twoFactor.confirmEnrolment(auth.getName(), request.code(), ClientAddress.of(http, trustedHops)));
    }

    @PostMapping("/verify")
    @Operation(summary = "Sign in with a code from the app or a backup code; returns a session for X-Admin-Session")
    public SessionResponse verify(Authentication auth, @RequestBody CodeRequest request, HttpServletRequest http) {
        return response(twoFactor.verify(auth.getName(), request.code(), ClientAddress.of(http, trustedHops)));
    }

    @PostMapping("/backup-codes")
    @Operation(summary = "Replace the backup codes (needs a current code from the app)")
    public BackupCodesResponse backupCodes(Authentication auth, @RequestBody CodeRequest request) {
        return new BackupCodesResponse(twoFactor.regenerateBackupCodes(auth.getName(), request.code()));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the second-factor session")
    public void logout(@RequestHeader(value = TwoFactorFilter.SESSION_HEADER, required = false) String session) {
        twoFactor.signOut(session);
    }

    private static SessionResponse response(TwoFactorService.Verified v) {
        return new SessionResponse(v.session().token(), v.session().expiresAt(), v.backupCodes(), v.backupCodesLeft());
    }
}
