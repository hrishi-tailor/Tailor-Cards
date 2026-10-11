package com.tailorcards.api.buylistchat.email;

import lombok.extern.slf4j.Slf4j;

/**
 * Development sender (EMAIL_PROVIDER=log): writes the message to the application log instead of
 * sending it, so one-time codes can be read locally. Never use in production.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("[DEV EMAIL] to={} subject='{}'\n{}", to, subject, textBody);
    }
}
