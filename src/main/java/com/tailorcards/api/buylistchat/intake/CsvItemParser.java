package com.tailorcards.api.buylistchat.intake;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses CSV uploads in Java. A recognised header (name/set/number/quantity/condition/variant)
 * gives structured rows; otherwise each row is treated as free text for {@link ItemNormalizer}.
 * Cells are data only and are never interpreted.
 */
@Component
public class CsvItemParser {

    public record Parsed(List<ItemInput> structured, List<String> freeText, int rowCount) {}

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("name", "name"), Map.entry("card", "name"), Map.entry("card name", "name"),
            Map.entry("card_name", "name"), Map.entry("product name", "name"), Map.entry("product", "name"),
            Map.entry("set", "set"), Map.entry("set name", "set"), Map.entry("set_name", "set"), Map.entry("expansion", "set"),
            Map.entry("number", "number"), Map.entry("card number", "number"), Map.entry("card_number", "number"),
            Map.entry("collector number", "number"), Map.entry("no", "number"), Map.entry("#", "number"),
            Map.entry("quantity", "quantity"), Map.entry("qty", "quantity"), Map.entry("count", "quantity"),
            Map.entry("amount", "quantity"),
            Map.entry("condition", "condition"), Map.entry("cond", "condition"),
            Map.entry("variant", "variant"), Map.entry("printing", "variant"), Map.entry("finish", "variant"),
            Map.entry("grading", "grading"), Map.entry("grade", "grading"), Map.entry("slab", "grading"));

    public Parsed parse(byte[] content, int maxRows, ItemNormalizer normalizer) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = parseRows(text);
        rows.removeIf(r -> r.stream().allMatch(String::isBlank));
        if (rows.isEmpty()) {
            return new Parsed(List.of(), List.of(), 0);
        }
        Map<String, Integer> columns = headerColumns(rows.getFirst());
        if (!columns.containsKey("name")) {
            List<String> free = rows.stream().map(r -> String.join(" ", r).trim()).filter(s -> !s.isEmpty()).toList();
            if (free.size() > maxRows) {
                throw new IllegalArgumentException("A list can have at most " + maxRows + " items.");
            }
            return new Parsed(List.of(), free, free.size());
        }
        List<List<String>> data = rows.subList(1, rows.size());
        if (data.size() > maxRows) {
            throw new IllegalArgumentException("A list can have at most " + maxRows + " items.");
        }
        List<ItemInput> items = new ArrayList<>();
        for (List<String> row : data) {
            String name = cell(row, columns.get("name"), 200);
            if (name == null) {
                continue;
            }
            int quantity = 1;
            String qty = cell(row, columns.get("quantity"), 10);
            if (qty != null && qty.matches("\\d{1,6}")) {
                quantity = Integer.parseInt(qty);
            }
            boolean bulk = name.toLowerCase(Locale.ROOT).contains("bulk");
            items.add(new ItemInput(bulk ? "BULK" : "CARD", name, cell(row, columns.get("set"), 150),
                    cell(row, columns.get("number"), 40), ItemNormalizer.normalizeVariant(cell(row, columns.get("variant"), 40)),
                    ItemNormalizer.normalizeCondition(cell(row, columns.get("condition"), 30)),
                    normalizer.clampQuantity(quantity, bulk), String.join(", ", row), null,
                    ItemNormalizer.normalizeGrading(cell(row, columns.get("grading"), 30))));
        }
        return new Parsed(items, List.of(), data.size());
    }

    private static Map<String, Integer> headerColumns(List<String> header) {
        Map<String, Integer> columns = new java.util.HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = HEADER_ALIASES.get(header.get(i).trim().toLowerCase(Locale.ROOT));
            if (key != null) {
                columns.putIfAbsent(key, i);
            }
        }
        return columns;
    }

    private static String cell(List<String> row, Integer index, int max) {
        if (index == null || index >= row.size()) {
            return null;
        }
        String value = row.get(index).replaceAll("[\\p{Cntrl}<>]", " ").trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    /** RFC 4180-style: commas, double-quoted fields, "" escapes, CRLF or LF. */
    static List<List<String>> parseRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
