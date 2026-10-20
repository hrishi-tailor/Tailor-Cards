package com.tailorcards.api.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CSV rows")
class CsvRowsTest {

    @Test
    @DisplayName("Quoted fields keep commas, line breaks and escaped quotes")
    void quotedFields() {
        List<List<String>> rows = CsvRows.parse("title,notes\r\n\"Charizard, Base\",\"say \"\"hi\"\"\nthere\"\n");
        assertThat(rows).containsExactly(
                List.of("title", "notes"),
                List.of("Charizard, Base", "say \"hi\"\nthere"));
    }

    @Test
    @DisplayName("Empty cells and a last line without a newline are kept")
    void emptyCellsAndLastLine() {
        assertThat(CsvRows.parse("a,,c\n1,2,")).containsExactly(List.of("a", "", "c"), List.of("1", "2", ""));
    }

    @Test
    @DisplayName("A byte-order mark from a spreadsheet export is removed")
    void byteOrderMark() {
        byte[] content = "﻿title\nMew".getBytes(StandardCharsets.UTF_8);
        assertThat(CsvRows.parse(CsvRows.decode(content))).containsExactly(List.of("title"), List.of("Mew"));
    }
}
