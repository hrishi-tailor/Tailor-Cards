package com.tailorcards.api.pipeline;

import java.util.Arrays;
import java.util.Optional;

/**
 * The tables the warehouse export can read, each as a fixed query (no caller-supplied SQL). Free-text notes,
 * descriptions and photo links are left out: the warehouse needs numbers and keys, not what was written about a deal.
 */
public enum ExportDataset {

    LEDGER_LOTS("ledger_lots", """
            select id, title, kind, card_ref, quantity, total_cost_cad, acquired_on, acquired_channel, listed_on,
                   listed_price_cad, source, created_at, updated_at
            from ledger_lots where id > ? order by id limit ?"""),

    LEDGER_SALES("ledger_sales", """
            select id, lot_id, sold_on, quantity, unit_price_cad, channel, payment_method, source, created_at
            from ledger_sales where id > ? order by id limit ?"""),

    PRODUCTS("products", """
            select p.id, p.name, c.name as category, p.card_set, p.card_number, p.condition, p.grading, p.price,
                   p.stock, p.cost_basis, p.pokemontcg_id, p.status
            from products p left join categories c on c.id = p.category_id
            where p.id > ? order by p.id limit ?"""),

    PRICE_SNAPSHOTS("price_snapshots", """
            select id, card_id, price_usd, price_cad, source, fetched_at
            from price_snapshots where id > ? order by id limit ?""");

    private final String key;
    private final String sql;

    ExportDataset(String key, String sql) {
        this.key = key;
        this.sql = sql;
    }

    public String key() {
        return key;
    }

    String sql() {
        return sql;
    }

    public static Optional<ExportDataset> fromKey(String key) {
        return Arrays.stream(values()).filter(d -> d.key.equals(key)).findFirst();
    }
}
