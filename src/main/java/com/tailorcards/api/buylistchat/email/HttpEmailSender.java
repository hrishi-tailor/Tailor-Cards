package com.tailorcards.api.buylistchat.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends email through a Resend-compatible HTTP API (EMAIL_PROVIDER=http): POST JSON
 * {from, to, subject, text} with a Bearer EMAIL_API_KEY. Failures are logged, never rethrown.
 */
@Slf4j
public class HttpEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String from;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpEmailSender(String apiUrl, String apiKey, String from) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    HttpEmailSender(RestClient restClient, String from) {
        this.restClient = restClient;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String textBody) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "from", from, "to", List.of(to), "subject", subject, "text", textBody));
            restClient.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("Email sent: subject='{}'", subject);
        } catch (RestClientResponseException e) {
            log.warn("Email provider rejected message (HTTP {}), subject='{}'", e.getStatusCode().value(), subject);
        } catch (Exception e) {
            log.warn("Email send failed ({}), subject='{}'", e.getClass().getSimpleName(), subject);
        }
    }
}
