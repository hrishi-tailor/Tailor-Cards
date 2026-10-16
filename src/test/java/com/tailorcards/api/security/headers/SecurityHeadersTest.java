package com.tailorcards.api.security.headers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Security headers")
class SecurityHeadersTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("API responses: nothing may run or frame them; HSTS, nosniff, no referrer, no browser features")
    void apiHeaders() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"));
        // Error responses too
        mvc.perform(get("/api/admin/trade-assistant/buy-rules"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.startsWith("default-src 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("Swagger UI gets a policy that lets its own scripts run, still no framing")
    void docsHeaders() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("The frontend sends the same headers on Vercel and Render, with no inline or eval'd scripts allowed")
    @SuppressWarnings("unchecked")
    void frontendHeaders() throws Exception {
        Map<String, String> vercel = new LinkedHashMap<>();
        JsonNode config = new ObjectMapper().readTree(Path.of("frontend/vercel.json").toFile());
        config.path("headers").get(0).path("headers").forEach(h -> vercel.put(h.path("key").asText(), h.path("value").asText()));

        Map<String, Object> render = new Yaml().load(Files.readString(Path.of("render.yaml")));
        Map<String, String> renderHeaders = new LinkedHashMap<>();
        for (Map<String, Object> service : (List<Map<String, Object>>) render.get("services")) {
            if ("static".equals(service.get("runtime"))) {
                for (Map<String, String> h : (List<Map<String, String>>) service.get("headers")) {
                    renderHeaders.put(h.get("name"), h.get("value"));
                }
            }
        }
        assertThat(renderHeaders).isEqualTo(vercel);
        assertThat(vercel).containsKeys("Content-Security-Policy", "Strict-Transport-Security", "X-Frame-Options",
                "Referrer-Policy", "Permissions-Policy", "X-Content-Type-Options");

        String scriptSrc = vercel.get("Content-Security-Policy").split("script-src")[1].split(";")[0];
        assertThat(scriptSrc).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        assertThat(vercel.get("Content-Security-Policy")).contains("frame-ancestors 'none'").contains("object-src 'none'");
        assertThat(vercel.get("Permissions-Policy")).contains("camera=(self)"); // the card scanner
        // No inline scripts left for the policy to block
        assertThat(Files.readString(Path.of("frontend/index.html"))).doesNotContainPattern("<script>(?s).*?</script>");
    }
}
