package com.tailorcards.api.security.twofactor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** app.security.two-factor.* */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.two-factor")
public class TwoFactorProperties {
    /** Require a TOTP code (or backup code) for every admin request. */
    private boolean enabled = true;
    /** Base64 of 32 random bytes (openssl rand -base64 32); encrypts TOTP secrets at rest. */
    private String encryptionKey;
    /** How long a second-factor session lasts. */
    private int sessionHours = 12;
    /** Name shown in the authenticator app. */
    private String issuer = "Tailor Cards";
    private int backupCodes = 10;
}
