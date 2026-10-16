package com.tailorcards.api.buylistchat.email;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Sends email through Amazon SES (EMAIL_PROVIDER=ses), authenticated by the server's IAM role (no keys in config).
 * The region comes from AWS_REGION. Failures are logged, never rethrown.
 */
@Slf4j
public class SesEmailSender implements EmailSender {

    private final SesV2Client ses;
    private final String from;

    public SesEmailSender(String from) {
        this(SesV2Client.builder().httpClient(UrlConnectionHttpClient.create()).build(), from);
    }

    SesEmailSender(SesV2Client ses, String from) {
        this.ses = ses;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String textBody) {
        try {
            ses.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(from)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder().text(Content.builder().data(textBody).charset("UTF-8").build()).build())
                            .build()).build())
                    .build());
            log.info("Email sent via SES: subject='{}'", subject);
        } catch (Exception e) {
            log.warn("SES send failed ({}), subject='{}'", e.getClass().getSimpleName(), subject);
        }
    }
}
