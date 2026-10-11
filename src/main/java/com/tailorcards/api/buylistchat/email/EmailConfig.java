package com.tailorcards.api.buylistchat.email;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Slf4j
@Configuration
public class EmailConfig {

    @Bean
    public EmailSender emailSender(BuylistChatProperties properties) {
        BuylistChatProperties.Email email = properties.getEmail();
        String provider = email.getProvider() == null ? "log" : email.getProvider().trim().toLowerCase(Locale.ROOT);
        if ("http".equals(provider)) {
            if (email.getApiKey().isBlank() || email.getFrom().isBlank()) {
                throw new IllegalStateException("EMAIL_PROVIDER=http requires EMAIL_API_KEY and EMAIL_FROM.");
            }
            return new HttpEmailSender(email.getApiUrl(), email.getApiKey(), email.getFrom());
        }
        if (properties.isEnabled()) {
            log.warn("Buylist chat is enabled with EMAIL_PROVIDER=log: sign-in codes are only written to the log.");
        }
        return new LoggingEmailSender();
    }
}
