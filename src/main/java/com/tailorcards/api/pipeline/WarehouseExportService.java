package com.tailorcards.api.pipeline;

import com.tailorcards.api.pipeline.dto.ExportPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads app tables page by page for the warehouse export (see docs/warehouse.md). */
@Service
public class WarehouseExportService {

    public static final int DEFAULT_LIMIT = 1_000;
    public static final int MAX_LIMIT = 5_000;

    private final JdbcTemplate jdbc;

    public WarehouseExportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ExportPage page(ExportDataset dataset, long after, int limit) {
        if (after < 0) {
            throw new IllegalArgumentException("after must be 0 or more");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(dataset.sql(), after, limit).stream()
                .map(WarehouseExportService::normalise)
                .toList();
        Long nextAfter = rows.size() < limit ? null : ((Number) rows.getLast().get("id")).longValue();
        return new ExportPage(dataset.key(), rows, nextAfter);
    }

    /** Lower-case column names; SQL dates and timestamps as ISO strings, so every database renders them alike. */
    private static Map<String, Object> normalise(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((column, value) -> out.put(column.toLowerCase(Locale.ROOT), iso(value)));
        return out;
    }

    private static Object iso(Object value) {
        return switch (value) {
            case Date date -> date.toLocalDate().toString();
            case Timestamp timestamp -> timestamp.toInstant().toString();
            case OffsetDateTime time -> time.toInstant().toString();
            case null -> null;
            default -> value;
        };
    }
}
