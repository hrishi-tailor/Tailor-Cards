package com.tailorcards.api.buylistchat.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.BuylistChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Cloudflare Turnstile check on code requests; a no-op unless TURNSTILE_SECRET is set. */
@Slf4j
@Component
public class TurnstileVerifier {

    private final BuylistChatProperties.Turnstile config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurnstileVerifier(BuylistChatProperties properties) {
        this.config = properties.getTurnstile();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return config.getSecret() != null && !config.getSecret().isBlank();
    }

    /** True when Turnstile is disabled or the token verifies. Fails closed on errors. */
    public boolean verify(String token, String remoteIp) {
        if (!isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", config.getSecret());
            form.add("response", token);
            if (remoteIp != null) {
                form.add("remoteip", remoteIp);
            }
            String body = restClient.post().uri(config.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            return root.path("success").asBoolean(false);
        } catch (Exception e) {
            log.warn("Turnstile verification failed ({})", e.getClass().getSimpleName());
            return false;
        }
    }
}
