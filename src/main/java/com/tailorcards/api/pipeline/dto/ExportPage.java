package com.tailorcards.api.pipeline.dto;

import java.util.List;
import java.util.Map;

/**
 * One page of a dataset, in id order. Columns are snake_case; dates are ISO strings (timestamps in UTC). Ask for the
 * next page with {@code after=nextAfter}; {@code nextAfter} is null on the last page.
 */
public record ExportPage(String dataset, List<Map<String, Object>> rows, Long nextAfter) {
}
