package com.tailorcards.api.security.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.security.twofactor.Totp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.two-factor.enabled=true",
        "app.security.login.max-failures=5",
        "app.security.login.max-failures-per-ip=8"
})
@DisplayName("Admin login lockout")
class LoginLockoutIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Wrong passwords and wrong codes lock the account, even for the right password and code")
    void lockout() throws Exception {
        // Set up two-factor for admin
        var enrol = new ObjectMapper().readTree(mvc.perform(post("/api/auth/2fa/enrol")
                .with(httpBasic("admin", "test-admin-password"))).andReturn().getResponse().getContentAsString());
        byte[] secret = Totp.fromBase32(enrol.path("secret").asText());
        long step = Totp.step(Instant.now().getEpochSecond());
        mvc.perform(post("/api/auth/2fa/enrol/confirm").with(httpBasic("admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + Totp.code(secret, step) + "\"}"))
                .andExpect(status().isOk());

        // 3 wrong passwords and 2 wrong codes from different IPs: 5 failures on the account
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/auth/verify").with(httpBasic("admin", "wrong-" + i)).header("X-Forwarded-For", "10.0.0." + i))
                    .andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/2fa/verify").with(httpBasic("admin", "test-admin-password"))
                    .header("X-Forwarded-For", "10.0.1." + i).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"abcdef\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // Locked: the right password and a right code are refused before being checked
        mvc.perform(post("/api/auth/2fa/verify").with(httpBasic("admin", "test-admin-password"))
                        .header("X-Forwarded-For", "10.0.2.1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.code(secret, step + 1) + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("too_many_attempts"));
        // Customers are unaffected
        mvc.perform(get("/api/products").header("X-Forwarded-For", "10.0.2.1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Too many failures from one IP block it, whatever the usernames; other IPs still work")
    void ipBlock() throws Exception {
        for (int i = 0; i < 8; i++) {
            mvc.perform(get("/api/auth/verify").with(httpBasic("guess" + i, "x")).header("X-Forwarded-For", "203.0.113.7"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/auth/verify").with(httpBasic("another-guess", "x")).header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isTooManyRequests());
        // The address the proxy saw is the last hop: a spoofed first hop doesn't get around the block
        mvc.perform(get("/api/auth/verify").with(httpBasic("another-guess", "x")).header("X-Forwarded-For", "1.2.3.4, 203.0.113.7"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/auth/verify").with(httpBasic("another-guess", "x")).header("X-Forwarded-For", "198.51.100.9"))
                .andExpect(status().isUnauthorized());
    }
}
