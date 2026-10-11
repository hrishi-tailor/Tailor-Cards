package com.tailorcards.api.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses and validates the model's JSON reply into a {@link ListingDraft}. Unknown keys (for
 * example a "price" the model was told never to produce) are dropped; anything that breaks the
 * schema or the content rules is reported so the caller can retry once.
 */
class ListingDraftParser {

    static final int TITLE_MAX = 80;
    static final int DESCRIPTION_MAX = 600;
    static final int CONDITION_NOTES_MAX = 500;
    static final int SHORT_FIELD_MAX = 100;
    static final int UNCERTAINTIES_MAX = 10;
    static final int UNCERTAINTY_MAX = 200;

    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{1F1E6}-\\x{1F1FF}]");

    // Claims the description may not make: prices/value, shipping promises, print runs, populations
    private static final Pattern FORBIDDEN_CLAIMS = Pattern.compile(
            "[$€£¥]|\\b(cad|usd|price[ds]?|pricing|worth|value[ds]?|valuable|invest\\w*|ship(s|ping|ped)?|"
                    + "delivery|print run|population|pop report|guarantee[ds]?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MARKUP = Pattern.compile("<[a-zA-Z/!]|\\*\\*|^#+\\s|```", Pattern.MULTILINE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    ListingDraft parse(String responseText) {
        List<String> problems = new ArrayList<>();
        JsonNode root = readJson(responseText);
        if (root == null || !root.isObject()) {
            throw new ListingDraftValidationException(List.of("Reply was not a single JSON object."));
        }

        String cardName = text(root, "cardName", SHORT_FIELD_MAX * 2, true, problems);
        String setName = text(root, "setName", SHORT_FIELD_MAX, false, problems);
        String cardNumber = text(root, "cardNumber", SHORT_FIELD_MAX, false, problems);
        String rarity = text(root, "rarity", SHORT_FIELD_MAX, false, problems);
        String language = text(root, "language", SHORT_FIELD_MAX, false, problems);
        ListingCondition condition = enumValue(root, "condition", ListingCondition.class, problems);
        String gradingCompany = text(root, "gradingCompany", 20, false, problems);
        String grade = text(root, "grade", 20, false, problems);
        if ((gradingCompany == null) != (grade == null)) {
            problems.add("gradingCompany and grade must both be set (slab visible) or both be null.");
        }

        Boolean isSealed = null;
        JsonNode sealedNode = root.get("isSealed");
        if (sealedNode == null || !sealedNode.isBoolean()) {
            problems.add("isSealed must be a boolean.");
        } else {
            isSealed = sealedNode.booleanValue();
        }

        String title = text(root, "title", TITLE_MAX, true, problems);
        String description = text(root, "description", DESCRIPTION_MAX, true, problems);
        String conditionNotes = text(root, "conditionNotes", CONDITION_NOTES_MAX, false, problems);
        ListingConfidence confidence = enumValue(root, "confidence", ListingConfidence.class, problems);
        List<String> uncertainties = uncertainties(root, problems);

        checkPlainText("title", title, problems);
        checkPlainText("description", description, problems);
        if (description != null && FORBIDDEN_CLAIMS.matcher(description).find()) {
            problems.add("description must not mention prices, value, shipping, print runs or populations.");
        }
        if (title != null && FORBIDDEN_CLAIMS.matcher(title).find()) {
            problems.add("title must not mention prices, value, shipping, print runs or populations.");
        }

        for (String field : new String[]{cardName, setName, cardNumber, rarity, language, gradingCompany, grade,
                title, description, conditionNotes, String.join(" ", uncertainties)}) {
            if (ListingPrompts.leaksSystemPrompt(field)) {
                problems.add("Reply must not repeat the instructions.");
                break;
            }
        }

        if (!problems.isEmpty()) {
            throw new ListingDraftValidationException(problems);
        }

        return new ListingDraft(cardName, setName, cardNumber, rarity, language, condition,
                gradingCompany, grade, isSealed, title, description, conditionNotes, confidence,
                List.copyOf(uncertainties));
    }

    private JsonNode readJson(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }
        String cleaned = responseText.trim();
        // Tolerate a markdown code fence around the object, nothing else
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        try {
            return objectMapper.readTree(cleaned.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode root, String field, int max, boolean required, List<String> problems) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                problems.add(field + " is required.");
            }
            return null;
        }
        if (!node.isTextual()) {
            problems.add(field + " must be a string or null.");
            return null;
        }
        String value = node.textValue().trim();
        if (value.isEmpty()) {
            if (required) {
                problems.add(field + " must not be blank.");
            }
            return null;
        }
        if (value.length() > max) {
            problems.add(field + " must be at most " + max + " characters.");
        }
        return value;
    }

    private <E extends Enum<E>> E enumValue(JsonNode root, String field, Class<E> type, List<String> problems) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            problems.add(field + " is required.");
            return null;
        }
        try {
            return Enum.valueOf(type, node.textValue().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(field + " has an unsupported value.");
            return null;
        }
    }

    private List<String> uncertainties(JsonNode root, List<String> problems) {
        JsonNode node = root.get("uncertainties");
        if (node == null || !node.isArray()) {
            problems.add("uncertainties must be an array of strings.");
            return List.of();
        }
        if (node.size() > UNCERTAINTIES_MAX) {
            problems.add("uncertainties must have at most " + UNCERTAINTIES_MAX + " items.");
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                problems.add("uncertainties must contain only strings.");
                continue;
            }
            String value = item.textValue().trim();
            if (value.length() > UNCERTAINTY_MAX) {
                problems.add("each uncertainty must be at most " + UNCERTAINTY_MAX + " characters.");
            } else if (!value.isEmpty()) {
                items.add(value);
            }
        }
        return items;
    }

    private void checkPlainText(String field, String value, List<String> problems) {
        if (value == null) {
            return;
        }
        if (EMOJI.matcher(value).find()) {
            problems.add(field + " must not contain emojis.");
        }
        if (MARKUP.matcher(value).find()) {
            problems.add(field + " must be plain text without markup.");
        }
    }
}
