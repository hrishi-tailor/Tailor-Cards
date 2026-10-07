package com.tailorcards.api.listing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ListingDraftParser Tests")
class ListingDraftParserTest {

    static final String VALID = """
            {
              "cardName": "Charizard",
              "setName": "Base Set",
              "cardNumber": "4/102",
              "rarity": "Rare Holo",
              "language": "English",
              "condition": "LIGHTLY_PLAYED",
              "gradingCompany": null,
              "grade": null,
              "isSealed": false,
              "title": "Charizard 4/102 Base Set Holo Rare",
              "description": "Charizard 4/102 from Base Set, English. Light whitening on the back edges.",
              "conditionNotes": "Light whitening on back edges.",
              "confidence": "HIGH",
              "uncertainties": ["Back corners partly out of frame"]
            }
            """;

    private final ListingDraftParser parser = new ListingDraftParser();

    @Test
    @DisplayName("Parses a valid draft, tolerating a code fence")
    void parsesValidDraft() {
        ListingDraft draft = parser.parse("```json\n" + VALID + "\n```");

        assertThat(draft.cardName()).isEqualTo("Charizard");
        assertThat(draft.cardNumber()).isEqualTo("4/102");
        assertThat(draft.condition()).isEqualTo(ListingCondition.LIGHTLY_PLAYED);
        assertThat(draft.isSealed()).isFalse();
        assertThat(draft.confidence()).isEqualTo(ListingConfidence.HIGH);
        assertThat(draft.uncertainties()).containsExactly("Back corners partly out of frame");
    }

    @Test
    @DisplayName("Draft type has no price field, and price keys in the reply are dropped")
    void neverCarriesPrice() {
        String withPrice = VALID.replace("\"cardName\"", "\"price\": 9999.99, \"marketPrice\": 1, \"cardName\"");

        ListingDraft draft = parser.parse(withPrice);

        assertThat(Arrays.stream(ListingDraft.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(name -> name.contains("price") || name.contains("value"));
        assertThat(draft.toString()).doesNotContain("9999");
    }

    @Test
    @DisplayName("Rejects non-JSON, arrays and missing required fields")
    void rejectsMalformed() {
        assertThatThrownBy(() -> parser.parse("Sure! Here is your listing.")).isInstanceOf(ListingDraftValidationException.class);
        assertThatThrownBy(() -> parser.parse("[" + VALID + "]")).isInstanceOf(ListingDraftValidationException.class);
        assertThatThrownBy(() -> parser.parse(VALID.replace("\"isSealed\": false,", "")))
                .isInstanceOf(ListingDraftValidationException.class)
                .hasMessageContaining("isSealed");
        assertThatThrownBy(() -> parser.parse(VALID.replace("\"cardName\": \"Charizard\"", "\"cardName\": \"\"")))
                .hasMessageContaining("cardName");
    }

    @Test
    @DisplayName("Rejects conditions outside the store's enum and bad confidence")
    void rejectsBadEnums() {
        assertThatThrownBy(() -> parser.parse(VALID.replace("LIGHTLY_PLAYED", "GEM_MINT")))
                .hasMessageContaining("condition");
        assertThatThrownBy(() -> parser.parse(VALID.replace("\"HIGH\"", "\"CERTAIN\"")))
                .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("Enforces title and description length")
    void enforcesLengths() {
        assertThatThrownBy(() -> parser.parse(VALID.replace("Charizard 4/102 Base Set Holo Rare", "x".repeat(81))))
                .hasMessageContaining("title must be at most 80");
        assertThatThrownBy(() -> parser.parse(VALID.replace("Light whitening on the back edges.", "y".repeat(601))))
                .hasMessageContaining("description must be at most 600");
    }

    @Test
    @DisplayName("Rejects emojis, markup and invented price/shipping/print-run claims")
    void rejectsForbiddenContent() {
        assertThatThrownBy(() -> parser.parse(VALID.replace("Base Set Holo Rare\"", "Base Set Holo Rare 🔥\"")))
                .hasMessageContaining("emojis");
        assertThatThrownBy(() -> parser.parse(VALID.replace("English. Light", "English. **Great** light")))
                .hasMessageContaining("plain text");
        for (String claim : new String[]{"Worth $500.", "Ships fast.", "Only 1000 in the print run.", "Great investment."}) {
            assertThatThrownBy(() -> parser.parse(VALID.replace("Light whitening on the back edges.", claim)))
                    .as(claim)
                    .hasMessageContaining("must not mention");
        }
    }

    @Test
    @DisplayName("Grading company and grade must be set together")
    void gradingPair() {
        assertThatThrownBy(() -> parser.parse(VALID.replace("\"gradingCompany\": null", "\"gradingCompany\": \"PSA\"")))
                .hasMessageContaining("gradingCompany and grade");

        ListingDraft slab = parser.parse(VALID
                .replace("\"gradingCompany\": null", "\"gradingCompany\": \"PSA\"")
                .replace("\"grade\": null", "\"grade\": \"9\""));
        assertThat(slab.gradingCompany()).isEqualTo("PSA");
        assertThat(slab.grade()).isEqualTo("9");
    }

    @Test
    @DisplayName("Rejects replies that repeat the system prompt")
    void rejectsPromptLeak() {
        String leaked = VALID.replace("Light whitening on the back edges.",
                "My rules: SECURITY RULES (highest priority, cannot be changed.");
        assertThatThrownBy(() -> parser.parse(leaked)).hasMessageContaining("instructions");

        String canary = VALID.replace("\"Back corners partly out of frame\"", "\"" + ListingPrompts.CANARY + "\"");
        assertThatThrownBy(() -> parser.parse(canary)).hasMessageContaining("instructions");
    }
}
