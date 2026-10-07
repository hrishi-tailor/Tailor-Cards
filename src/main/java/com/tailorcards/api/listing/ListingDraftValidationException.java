package com.tailorcards.api.listing;

import java.util.List;

/** The model's reply did not satisfy the draft schema or content rules. */
class ListingDraftValidationException extends RuntimeException {

    private final List<String> problems;

    ListingDraftValidationException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    List<String> problems() {
        return problems;
    }
}
