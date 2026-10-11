package com.tailorcards.api.buylistchat.intake;

/**
 * A line item before resolution. kind is CARD or BULK; condition is NM, LP, MP, HP, DMG or UNKNOWN.
 * cardId is set only when the item came from a card the customer picked via the chat tools; grading
 * is the slab grade (e.g. "PSA 10") or null for raw cards.
 */
public record ItemInput(String kind, String name, String setName, String cardNumber, String variant,
                        String condition, int quantity, String inputText, String cardId, String grading) {

    public static ItemInput card(String name, String setName, String cardNumber, String variant,
                                 String condition, int quantity, String inputText) {
        return new ItemInput("CARD", name, setName, cardNumber, variant, condition, quantity, inputText, null, null);
    }
}
