package com.tailorcards.api.dto;

public record SnapshotSyncResponse(
        String status,
        int cardsSynced,
        String message
) {}
