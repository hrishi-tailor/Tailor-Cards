package com.tailorcards.api.security.twofactor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.security.two-factor.enabled=true")
@DisplayName("Admin two-factor login")
class TwoFactorLoginIntegrationTest {

    private static final String ADMIN_ROUTE = "/api/admin/trade-assistant/buy-rules";

    @Autowired
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "test-admin-password");
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions code(String path, String code) throws Exception {
        return mvc.perform(post(path).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    @Test
    @DisplayName("Password alone is not enough; setup by QR secret, then every admin route needs the session")
    void fullFlow() throws Exception {
        // Password only: refused with a clear error, customer routes unaffected
        mvc.perform(get(ADMIN_ROUTE).with(admin())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("two_factor_required")).andExpect(jsonPath("$.enrolled").value(false));
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/2fa/status")).andExpect(status().isUnauthorized()); // needs the password
        mvc.perform(get("/api/auth/2fa/status").with(admin())).andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.enrolled").value(false));

        // Setup: the secret from the QR code generates the codes
        JsonNode enrol = body(mvc.perform(post("/api/auth/2fa/enrol").with(admin())).andExpect(status().isOk()));
        assertThat(enrol.path("otpauthUri").asText()).startsWith("otpauth://totp/Tailor%20Cards:admin?secret=");
        byte[] secret = Totp.fromBase32(enrol.path("secret").asText());
        long step = Totp.step(Instant.now().getEpochSecond());
        code("/api/auth/2fa/enrol/confirm", "000000".equals(Totp.code(secret, step)) ? "111111" : "000000")
                .andExpect(status().isUnauthorized()); // wrong code
        JsonNode confirmed = body(code("/api/auth/2fa/enrol/confirm", Totp.code(secret, step)).andExpect(status().isOk()));
        assertThat(confirmed.path("backupCodes")).hasSize(10);
        String session = confirmed.path("sessionToken").asText();

        // With the session: admin routes work; a made-up session doesn't
        mvc.perform(get(ADMIN_ROUTE).with(admin()).header("X-Admin-Session", session)).andExpect(status().isOk());
        mvc.perform(get(ADMIN_ROUTE).with(admin()).header("X-Admin-Session", "made-up")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/2fa/enrol").with(admin())).andExpect(status().isConflict()); // can't re-enrol over it

        // The same code can't be used twice; the next one can
        code("/api/auth/2fa/verify", Totp.code(secret, step)).andExpect(status().isUnauthorized());
        code("/api/auth/2fa/verify", Totp.code(secret, step + 1)).andExpect(status().isOk());

        // Backup codes work once
        String backup = confirmed.path("backupCodes").get(0).asText();
        JsonNode viaBackup = body(code("/api/auth/2fa/verify", backup.toLowerCase()).andExpect(status().isOk()));
        assertThat(viaBackup.path("backupCodesLeft").asInt()).isEqualTo(9);
        code("/api/auth/2fa/verify", backup).andExpect(status().isUnauthorized());

        // Signing out ends the session
        mvc.perform(post("/api/auth/2fa/logout").with(admin()).header("X-Admin-Session", session)).andExpect(status().isOk());
        mvc.perform(get(ADMIN_ROUTE).with(admin()).header("X-Admin-Session", session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.enrolled").value(true));
    }
}
