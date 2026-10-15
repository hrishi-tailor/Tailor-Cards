package com.tailorcards.api.buylistchat.scan;

import com.tailorcards.api.buylistchat.BuylistDraftService;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.PhoneLinkResponse;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.PhoneLinkStatus;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanAddRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Phone scan links")
class PhoneLinkServiceTest {

    private static final String EMAIL = "seller@example.com";

    private CardScanService scanner;
    private BuylistDraftService drafts;
    private MutableClock clock;
    private PhoneLinkService links;

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void setUp() {
        scanner = mock(CardScanService.class);
        drafts = mock(BuylistDraftService.class);
        clock = new MutableClock();
        links = new PhoneLinkService(scanner, drafts, clock);
    }

    @Test
    @DisplayName("A link scans and adds onto its own draft, and the computer sees what was added")
    void scanAndAddThroughLink() throws Exception {
        PhoneLinkResponse link = links.create(EMAIL, 7L, "d1");
        assertThat(link.token()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(links.status(EMAIL, "d1", link.token()).connected()).isFalse();

        links.open(link.token());
        MockMultipartFile front = new MockMultipartFile("front", "f.jpg", "image/jpeg", new byte[]{1});
        links.scan(link.token(), front, null);
        verify(scanner).scan(EMAIL, 7L, "d1", front, null);

        when(scanner.addScan(eq(EMAIL), eq("d1"), eq("s1"), any()))
                .thenReturn(new CardScanService.Added(null, "Gengar (Lost Origin Trainer Gallery TG06)"));
        assertThat(links.add(link.token(), "s1", new ScanAddRequest(null, "NM", 1)))
                .isEqualTo("Gengar (Lost Origin Trainer Gallery TG06)");

        PhoneLinkStatus status = links.status(EMAIL, "d1", link.token());
        assertThat(status.connected()).isTrue();
        assertThat(status.added()).containsExactly("Gengar (Lost Origin Trainer Gallery TG06)");
    }

    @Test
    @DisplayName("Links expire after 30 minutes")
    void expires() {
        PhoneLinkResponse link = links.create(EMAIL, 7L, "d1");
        clock.now = clock.now.plus(PhoneLinkService.TTL).plus(Duration.ofSeconds(1));
        assertThatThrownBy(() -> links.open(link.token())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
        assertThat(links.status(EMAIL, "d1", link.token()).expired()).isTrue();
    }

    @Test
    @DisplayName("A new code replaces the old one, and status is only visible to the draft's owner")
    void oneLinkPerDraftAndOwnerOnly() {
        PhoneLinkResponse first = links.create(EMAIL, 7L, "d1");
        PhoneLinkResponse second = links.create(EMAIL, 7L, "d1");
        assertThatThrownBy(() -> links.open(first.token())).isInstanceOf(ResponseStatusException.class);
        links.open(second.token());

        assertThatThrownBy(() -> links.status("other@example.com", "d1", second.token()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> links.status(EMAIL, "d2", second.token()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> links.open("made-up-token")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Only the draft's owner can make a link for it")
    void createChecksOwnership() {
        when(drafts.ownedOpenDraft("d9", EMAIL)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> links.create(EMAIL, 7L, "d9")).isInstanceOf(ResponseStatusException.class);
    }
}
