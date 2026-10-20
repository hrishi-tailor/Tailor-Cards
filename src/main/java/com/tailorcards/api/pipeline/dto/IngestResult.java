package com.tailorcards.api.pipeline.dto;

/** What a batch did: prices saved, and prices skipped because the same card, source and time was already saved. */
public record IngestResult(String runId, int saved, int duplicates) {
}
