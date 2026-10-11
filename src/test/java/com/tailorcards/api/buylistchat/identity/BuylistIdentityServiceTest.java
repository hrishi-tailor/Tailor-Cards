package com.tailorcards.api.buylistchat.identity;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.email.EmailSender;
import com.tailorcards.api.buylistchat.entity.BuylistChatSession;
import com.tailorcards.api.buylistchat.entity.BuylistOtpCode;
import com.tailorcards.api.buylistchat.repository.BuylistChatSessionRepository;
import com.tailorcards.api.buylistchat.repository.BuylistOtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Buylist email one-time codes and sessions")
class BuylistIdentityServiceTest {

    private BuylistOtpCodeRepository codes;
    private BuylistChatSessionRepository sessions;
    private EmailSender email;
    private TurnstileVerifier turnstile;
    private BuylistIdentityService service;
    private final Instant now = Instant.parse("2026-10-08T15:00:00Z");

    @BeforeEach
    void setUp() {
        codes = mock(BuylistOtpCodeRepository.class);
        sessions = mock(BuylistChatSessionRepository.class);
        email = mock(EmailSender.class);
        turnstile = mock(TurnstileVerifier.class);
        when(turnstile.verify(any(), any())).thenReturn(true);
        when(codes.findTopByEmailOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(i -> {
            BuylistChatSession s = i.getArgument(0);
            s.setId(7L);
            return s;
        });
        service = new BuylistIdentityService(codes, sessions, email, turnstile, new BuylistChatProperties(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private String requestAndCaptureCode() {
        service.requestCode("  Seller@Example.com ", null, "1.2.3.4");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq("seller@example.com"), anyString(), body.capture());
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(body.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private BuylistOtpCode savedCode() {
        ArgumentCaptor<BuylistOtpCode> saved = ArgumentCaptor.forClass(BuylistOtpCode.class);
        verify(codes).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("Code is 6 digits, stored only as a hash, expires in 10 minutes")
    void storesHashOnly() {
        String code = requestAndCaptureCode();
        BuylistOtpCode stored = savedCode();

        assertThat(stored.getEmail()).isEqualTo("seller@example.com");
        assertThat(stored.getCodeHash()).hasSize(64).doesNotContain(code);
        assertThat(stored.getExpiresAt()).isEqualTo(now.plusSeconds(600));
        assertThat(stored.getAttempts()).isZero();
    }

    @Test
    @DisplayName("Correct code issues a session token stored as a hash; the code can't be reused")
    void verifiesAndIssuesSession() {
        String code = requestAndCaptureCode();
        BuylistOtpCode stored = savedCode();
        when(codes.findTopByEmailOrderByCreatedAtDesc("seller@example.com")).thenReturn(Optional.of(stored));

        BuylistIdentityService.IssuedSession session = service.verifyCode("seller@example.com", code, "1.2.3.4");

        assertThat(session.token()).hasSizeGreaterThan(30);
        ArgumentCaptor<BuylistChatSession> savedSession = ArgumentCaptor.forClass(BuylistChatSession.class);
        verify(sessions).save(savedSession.capture());
        assertThat(savedSession.getValue().getTokenHash()).isEqualTo(BuylistIdentityService.sha256(session.token()));
        assertThat(stored.getConsumedAt()).isNotNull();
        assertThatThrownBy(() -> service.verifyCode("seller@example.com", code, "1.2.3.4"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Five wrong attempts lock the code, even if the sixth is right")
    void maxAttempts() {
        String code = requestAndCaptureCode();
        BuylistOtpCode stored = savedCode();
        when(codes.findTopByEmailOrderByCreatedAtDesc("seller@example.com")).thenReturn(Optional.of(stored));
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verifyCode("seller@example.com", wrong, null))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.verifyCode("seller@example.com", code, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("Expired code is rejected")
    void expired() {
        BuylistOtpCode old = BuylistOtpCode.builder().email("seller@example.com").codeHash("x".repeat(64)).attempts(0)
                .createdAt(now.minusSeconds(900)).expiresAt(now.minusSeconds(300)).build();
        when(codes.findTopByEmailOrderByCreatedAtDesc("seller@example.com")).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.verifyCode("seller@example.com", "123456", null))
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Resend cooldown and Turnstile are enforced before any email is sent")
    void cooldownAndTurnstile() {
        BuylistOtpCode recent = BuylistOtpCode.builder().email("seller@example.com").createdAt(now.minusSeconds(20)).build();
        when(codes.findTopByEmailOrderByCreatedAtDesc("seller@example.com")).thenReturn(Optional.of(recent));
        assertThatThrownBy(() -> service.requestCode("seller@example.com", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        when(turnstile.verify(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.requestCode("other@example.com", "bad-token", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Turnstile is only checked when TURNSTILE_SECRET is set")
    void turnstileOptional() {
        BuylistChatProperties props = new BuylistChatProperties();
        assertThat(new TurnstileVerifier(props).verify(null, null)).isTrue();
        props.getTurnstile().setSecret("secret");
        assertThat(new TurnstileVerifier(props).verify(null, null)).isFalse();
    }

    @Test
    @DisplayName("Invalid email and bad session tokens are rejected")
    void validation() {
        assertThatThrownBy(() -> service.requestCode("not-an-email", null, null)).isInstanceOf(IllegalArgumentException.class);
        when(sessions.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("nope")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.authenticate(null)).isInstanceOf(ResponseStatusException.class);
    }
}
