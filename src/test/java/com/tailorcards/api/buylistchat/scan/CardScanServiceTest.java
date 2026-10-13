package com.tailorcards.api.buylistchat.scan;

import com.tailorcards.api.buylistchat.BuylistDraftService;
import com.tailorcards.api.buylistchat.BuylistLlmBudget;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanAddRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanResponse;
import com.tailorcards.api.buylistchat.entity.BuylistDraft;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.intake.ItemInput;
import com.tailorcards.api.buylistchat.pricing.DealCalculator;
import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.listing.ListingImageSanitizer;
import com.tailorcards.api.listing.ListingImageSanitizer.ImageType;
import com.tailorcards.api.listing.ListingImageSanitizer.SanitizedImage;
import com.tailorcards.api.service.BuylistStorageService;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.AnthropicResponse;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider.SetInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Card scanner")
class CardScanServiceTest {

    private static final String EMAIL = "seller@example.com";
    private static final String DRAFT = "draft-1";

    private AnthropicClient client;
    private CardLookupService lookup;
    private BuylistDraftService drafts;
    private BuylistLlmBudget budget;
    private CardScanService service;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(AnthropicClient.class);
        lookup = mock(CardLookupService.class);
        drafts = mock(BuylistDraftService.class);
        budget = mock(BuylistLlmBudget.class);
        ListingImageSanitizer sanitizer = mock(ListingImageSanitizer.class);
        BuylistStorageService storage = mock(BuylistStorageService.class);
        DealCalculator deals = mock(DealCalculator.class);
        when(client.isConfigured()).thenReturn(true);
        when(budget.hasBudget()).thenReturn(true);
        when(sanitizer.sanitize(any())).thenReturn(new SanitizedImage(ImageType.JPEG, new byte[]{1, 2, 3}, 1));
        when(storage.storeFile(any())).thenReturn("/uploads/front.jpg", "/uploads/back.jpg");
        when(deals.unitCashOffer(any(), any(), any())).thenReturn(new BigDecimal("6.50"));
        service = new CardScanService(client, sanitizer, storage, lookup, budget, new LlmRateLimiter(1000), drafts,
                deals, Clock.systemUTC());
    }

    private static CardMarketPrice card(String id, String name, String set, String number, String usd) {
        return new CardMarketPrice(id, name, set, number, "https://img/" + id, "https://img/" + id + "/high",
                usd == null ? null : new BigDecimal(usd), "TCGdex", null, "Pokemon", Map.of(), null, null, null);
    }

    private static CardScanReading reading(String name, String set, String number) {
        return new CardScanReading(true, List.of(), List.of(), name, set, null, number, "English", false, null, null,
                "LP+", Map.of("edges", "One white dot on the top edge"), 1, true, "HIGH");
    }

    private void modelReplies(String... texts) {
        var stub = when(client.sendContentMessage(anyString(), anyList()));
        for (String t : texts) {
            stub = stub.thenReturn(Optional.of(new AnthropicResponse(t, 1000, 200, 900, new BigDecimal("0.002"))));
        }
    }

    private static MockMultipartFile photo(String part) {
        return new MockMultipartFile(part, part + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    // ---------------------------------------------------------------- parsing

    @Test
    @DisplayName("Parses a reading, cleans text and keeps only known photo issues")
    void parsesReading() {
        CardScanReading r = CardScanReading.parse("""
                ```json
                {"readable": true, "retakeTips": [], "photoIssues": ["glare", "made_up"], "cardName": "Pikachu <b>",
                 "setName": "Lost Origin", "setCode": "LOR", "cardNumber": "TG05/TG30", "language": "English",
                 "isSealed": false, "gradingCompany": null, "grade": null, "condition": "lp+",
                 "conditionNotes": {"corners": "Sharp", "edges": "One white dot", "surface": null, "centering": null},
                 "whiteDots": 1, "needsBackPhoto": false, "confidence": "high"}
                ```""");
        assertThat(r.cardName()).isEqualTo("Pikachu b");
        assertThat(r.condition()).isEqualTo("LP+");
        assertThat(r.confidence()).isEqualTo("HIGH");
        assertThat(r.photoIssues()).containsExactly("glare");
        assertThat(r.conditionNotes()).containsOnlyKeys("corners", "edges");
        assertThat(r.whiteDots()).isEqualTo(1);
    }

    @Test
    @DisplayName("Slabs and sealed product have no raw condition")
    void slabsHaveNoCondition() {
        CardScanReading r = CardScanReading.parse("""
                {"readable": true, "cardName": "Charizard", "cardNumber": "4/102", "gradingCompany": "psa",
                 "grade": "10", "condition": "NM", "confidence": "MEDIUM"}""");
        assertThat(r.condition()).isEqualTo("UNKNOWN");
        assertThat(r.grading()).isEqualTo("PSA 10");
    }

    @Test
    @DisplayName("Rejects unknown conditions, missing names and leaked instructions")
    void rejectsInvalidReplies() {
        assertThatThrownBy(() -> CardScanReading.parse("{\"readable\": true, \"cardName\": \"Mew\", \"condition\": \"MINT\"}"))
                .isInstanceOf(CardScanReading.InvalidReading.class).hasMessageContaining("condition");
        assertThatThrownBy(() -> CardScanReading.parse("{\"readable\": true, \"condition\": \"NM\"}"))
                .hasMessageContaining("cardName");
        assertThatThrownBy(() -> CardScanReading.parse("Sure! " + CardScanPrompts.CANARY))
                .isInstanceOf(CardScanReading.InvalidReading.class);
        assertThatThrownBy(() -> CardScanReading.parse("no json here")).hasMessageContaining("JSON");
    }

    // ---------------------------------------------------------------- matching

    @Test
    @DisplayName("Matches by set and number, and rejects a different Pokemon at that number")
    void matchesBySetAndNumber() {
        when(lookup.setIdsFor("Lost Origin")).thenReturn(List.of("swsh11", "swsh11tg"));
        when(lookup.findBySetAndNumber(List.of("swsh11", "swsh11tg"), "TG05"))
                .thenReturn(Optional.of(card("swsh11tg-TG05", "Pikachu", "Lost Origin Trainer Gallery", "TG05", "12.00")));
        CardScanService.Match m = service.match(reading("Pikachu", "Lost Origin", "TG05/TG30"));
        assertThat(m.card().cardId()).isEqualTo("swsh11tg-TG05");

        when(lookup.findBySetAndNumber(List.of("swsh11", "swsh11tg"), "TG05"))
                .thenReturn(Optional.of(card("swsh11tg-TG05", "Mewtwo", "Lost Origin Trainer Gallery", "TG05", "12.00")));
        when(lookup.searchFlexible("Pikachu")).thenReturn(List.of());
        assertThat(service.match(reading("Pikachu", "Lost Origin", "TG05/TG30")).card()).isNull();
    }

    @Test
    @DisplayName("Promo numbers go to their promo set")
    void promoNumbers() {
        when(lookup.setIdsFor("SVP Black Star Promos")).thenReturn(List.of("svp"));
        when(lookup.findBySetAndNumber(List.of("svp"), "053"))
                .thenReturn(Optional.of(card("svp-053", "Mew ex", "SVP Black Star Promos", "053", "4.00")));
        assertThat(service.match(reading("Mew ex", null, "SVP 053")).card().cardId()).isEqualTo("svp-053");

        when(lookup.setIdsFor("SM Black Star Promos")).thenReturn(List.of("smp"));
        when(lookup.findBySetAndNumber(List.of("smp"), "SM60"))
                .thenReturn(Optional.of(card("smp-SM60", "Charizard GX", "SM Black Star Promos", "SM60", "9.00")));
        assertThat(service.match(reading("Charizard GX", null, "SM60")).card().cardId()).isEqualTo("smp-SM60");
    }

    @Test
    @DisplayName("Without a set, the printed set size picks between prints with the same number")
    void setSizeBreaksTies() {
        when(lookup.searchFlexible("Charizard")).thenReturn(List.of(
                card("base1-4", "Charizard", "Base Set", "4", null),
                card("base4-4", "Charizard", "Base Set 2", "4", null),
                card("sv3pt5-6", "Charizard ex", "151", "6", null)));
        when(lookup.sets()).thenReturn(List.of(new SetInfo("base1", "Base Set", 102), new SetInfo("base4", "Base Set 2", 130)));
        when(lookup.card("base1-4")).thenReturn(Optional.of(card("base1-4", "Charizard", "Base Set", "4", "400.00")));
        CardScanService.Match m = service.match(reading("Charizard", null, "4/102"));
        assertThat(m.card().cardId()).isEqualTo("base1-4");
        assertThat(m.card().marketUsd()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Ambiguous reads return candidates instead of guessing")
    void ambiguousReturnsCandidates() {
        when(lookup.searchFlexible("Umbreon")).thenReturn(List.of(
                card("a-1", "Umbreon", "A", "1", null), card("b-2", "Umbreon", "B", "2", null)));
        CardScanService.Match m = service.match(reading("Umbreon", null, null));
        assertThat(m.card()).isNull();
        assertThat(m.candidates()).extracting("cardId").containsExactly("a-1", "b-2");
    }

    // ---------------------------------------------------------------- scan and add

    @Test
    @DisplayName("A scan retries once after an invalid reply, then adds the confirmed card with its photo")
    void scanThenAdd() throws Exception {
        BuylistDraft draft = mock(BuylistDraft.class);
        BuylistDraftLine line = mock(BuylistDraftLine.class);
        when(line.getId()).thenReturn(7L);
        when(drafts.ownedOpenDraft(DRAFT, EMAIL)).thenReturn(draft);
        when(drafts.addItems(eq(draft), anyList())).thenReturn(List.of(line));
        when(lookup.setIdsFor("Lost Origin")).thenReturn(List.of("swsh11tg"));
        when(lookup.findBySetAndNumber(List.of("swsh11tg"), "TG05"))
                .thenReturn(Optional.of(card("swsh11tg-TG05", "Pikachu", "Lost Origin Trainer Gallery", "TG05", "10.00")));
        modelReplies("{\"readable\": true}", """
                {"readable": true, "cardName": "Pikachu", "setName": "Lost Origin", "cardNumber": "TG05/TG30",
                 "condition": "LP+", "whiteDots": 1, "needsBackPhoto": true, "confidence": "HIGH",
                 "conditionNotes": {"edges": "One white dot on the top edge"}}""");

        ScanResponse scan = service.scan(EMAIL, 1L, DRAFT, photo("front"), null);
        assertThat(scan.readable()).isTrue();
        assertThat(scan.card().cardId()).isEqualTo("swsh11tg-TG05");
        assertThat(scan.conditionLabel()).isEqualTo("LP+ (one white dot)");
        assertThat(scan.offerUnitUsd()).isEqualByComparingTo("6.50");
        verify(client, times(2)).sendContentMessage(anyString(), anyList());
        verify(budget).record(new BigDecimal("0.004"), 2);
        verify(drafts, never()).addItems(any(), anyList()); // nothing on the list until confirmed

        service.add(EMAIL, DRAFT, scan.scanId(), new ScanAddRequest(null, "LP+", 2));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemInput>> items = ArgumentCaptor.forClass(List.class);
        verify(drafts).addItems(eq(draft), items.capture());
        ItemInput item = items.getValue().getFirst();
        assertThat(item.cardId()).isEqualTo("swsh11tg-TG05");
        assertThat(item.condition()).isEqualTo("LP+");
        assertThat(item.quantity()).isEqualTo(2);
        verify(drafts).attachPhoto(draft, 7L, "/uploads/front.jpg");
        verify(drafts).appendMessage(eq(draft), eq("ASSISTANT"), org.mockito.ArgumentMatchers.contains("photo of the back"));

        // A scan is used once
        assertThatThrownBy(() -> service.add(EMAIL, DRAFT, scan.scanId(), new ScanAddRequest(null, null, 1)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Only the matched card or a suggested candidate can be added, and only by the same customer")
    void addGuards() throws Exception {
        when(drafts.ownedOpenDraft(DRAFT, EMAIL)).thenReturn(mock(BuylistDraft.class));
        when(lookup.searchFlexible("Umbreon")).thenReturn(List.of(
                card("a-1", "Umbreon", "A", "1", null), card("b-2", "Umbreon", "B", "2", null)));
        modelReplies("{\"readable\": true, \"cardName\": \"Umbreon\", \"condition\": \"NM\", \"confidence\": \"LOW\"}");
        ScanResponse scan = service.scan(EMAIL, 1L, DRAFT, photo("front"), null);

        assertThatThrownBy(() -> service.add("other@example.com", DRAFT, scan.scanId(), new ScanAddRequest("a-1", "NM", 1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.add(EMAIL, DRAFT, scan.scanId(), new ScanAddRequest("zzz-9", "NM", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("An unreadable photo comes back with retake tips and cannot be added")
    void unreadable() throws Exception {
        when(drafts.ownedOpenDraft(DRAFT, EMAIL)).thenReturn(mock(BuylistDraft.class));
        modelReplies("""
                {"readable": false, "retakeTips": ["Add more light or move near a window"], "photoIssues": ["too_dark"]}""");
        ScanResponse scan = service.scan(EMAIL, 1L, DRAFT, photo("front"), null);
        assertThat(scan.readable()).isFalse();
        assertThat(scan.retakeTips()).containsExactly("Add more light or move near a window");
        assertThatThrownBy(() -> service.add(EMAIL, DRAFT, scan.scanId(), new ScanAddRequest(null, null, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects oversized photos before calling the model")
    void rejectsLargePhotos() {
        when(drafts.ownedOpenDraft(DRAFT, EMAIL)).thenReturn(mock(BuylistDraft.class));
        MockMultipartFile big = new MockMultipartFile("front", "f.jpg", "image/jpeg", new byte[(int) CardScanService.MAX_PHOTO_BYTES + 1]);
        assertThatThrownBy(() -> service.scan(EMAIL, 1L, DRAFT, big, null)).isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).sendContentMessage(anyString(), anyList());
    }
}
