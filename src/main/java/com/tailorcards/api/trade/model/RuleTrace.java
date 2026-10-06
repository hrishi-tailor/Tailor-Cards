package com.tailorcards.api.trade.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RuleTrace {

    private final List<RuleTraceEntry> entries = new ArrayList<>();

    public RuleTrace() {}

    public void add(String rule, String description, String effect) {
        entries.add(new RuleTraceEntry(rule, description, effect, null));
    }

    public void add(String rule, String description, String effect, Number value) {
        entries.add(new RuleTraceEntry(rule, description, effect, value));
    }

    @JsonProperty("entries")
    public List<RuleTraceEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public String toSummaryString() {
        if (entries.isEmpty()) {
            return "No rules evaluated";
        }
        return entries.stream()
                .map(e -> e.rule() + ": " + (e.effect() != null ? e.effect() : e.description()))
                .collect(Collectors.joining(" | "));
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"entries\":[]}";
        }
    }

    @Override
    public String toString() {
        return toSummaryString();
    }
}
