package com.tailorcards.api.buylistchat.intake;

import com.tailorcards.api.common.CsvRows;
import org.springframework.stereotype.Component;

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
            Map.entry("item", "name"), Map.entry("title", "name"),
            Map.entry("type", "kind"), Map.entry("kind", "kind"), Map.entry("category", "kind"),
            Map.entry("set", "set"), Map.entry("set name", "set"), Map.entry("set_name", "set"), Map.entry("expansion", "set"),
            Map.entry("number", "number"), Map.entry("card number", "number"), Map.entry("card_number", "number"),
            Map.entry("collector number", "number"), Map.entry("no", "number"), Map.entry("#", "number"),
            Map.entry("quantity", "quantity"), Map.entry("qty", "quantity"), Map.entry("count", "quantity"),
            Map.entry("amount", "quantity"),
            Map.entry("condition", "condition"), Map.entry("cond", "condition"),
            Map.entry("variant", "variant"), Map.entry("printing", "variant"), Map.entry("finish", "variant"),
            Map.entry("grading", "grading"), Map.entry("grade", "grading"), Map.entry("slab", "grading"));

    public Parsed parse(byte[] content, int maxRows, ItemNormalizer normalizer) {
        List<List<String>> rows = CsvRows.parse(CsvRows.decode(content));
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
            String kindCol = cell(row, columns.get("kind"), 20);
            String condCol = cell(row, columns.get("condition"), 30);
            boolean bulk = name.toLowerCase(Locale.ROOT).contains("bulk")
                    || (kindCol != null && "bulk".equalsIgnoreCase(kindCol.trim()));
            boolean sealed = !bulk && ((kindCol != null && "sealed".equalsIgnoreCase(kindCol.trim()))
                    || (condCol != null && "sealed".equalsIgnoreCase(condCol.trim()))
                    || ItemNormalizer.isSealed(name));
            String kind = bulk ? "BULK" : (sealed ? "SEALED" : "CARD");
            items.add(new ItemInput(kind, name, cell(row, columns.get("set"), 150),
                    sealed ? null : cell(row, columns.get("number"), 40),
                    sealed ? null : ItemNormalizer.normalizeVariant(cell(row, columns.get("variant"), 40)),
                    sealed ? "SEALED" : ItemNormalizer.normalizeCondition(condCol),
                    normalizer.clampQuantity(quantity, bulk), String.join(", ", row), null,
                    sealed ? null : ItemNormalizer.normalizeGrading(cell(row, columns.get("grading"), 30))));
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
}
