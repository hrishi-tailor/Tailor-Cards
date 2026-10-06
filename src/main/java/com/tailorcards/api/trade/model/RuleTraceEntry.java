package com.tailorcards.api.trade.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleTraceEntry(
    String rule,
    String description,
    String effect,
    Number value
) {}
