package com.tailorcards.api.buylistchat.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SES email sender")
class SesEmailSenderTest {

    @Test
    @DisplayName("Sends a plain-text email from the configured address")
    void sends() {
        SesV2Client ses = mock(SesV2Client.class);
        new SesEmailSender(ses, "codes@staging.tailorcards.com").send("a@example.com", "Your code", "123456");

        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        assertThat(request.getValue().fromEmailAddress()).isEqualTo("codes@staging.tailorcards.com");
        assertThat(request.getValue().destination().toAddresses()).containsExactly("a@example.com");
        assertThat(request.getValue().content().simple().subject().data()).isEqualTo("Your code");
        assertThat(request.getValue().content().simple().body().text().data()).isEqualTo("123456");
    }

    @Test
    @DisplayName("A rejected send is logged, not thrown")
    void failureIsLogged() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SesV2Exception.builder().message("not verified").build());
        assertThatCode(() -> new SesEmailSender(ses, "x@example.com").send("a@example.com", "s", "b")).doesNotThrowAnyException();
    }
}
