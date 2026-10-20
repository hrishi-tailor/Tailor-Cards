package com.tailorcards.api.pipeline;

import com.tailorcards.api.pipeline.dto.ExportPage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only export of the ledger, products and price snapshots for the AWS warehouse. Signed like the price
 * pipeline's endpoints (the query string is part of the signature) and off without a shared secret.
 */
@RestController
@RequestMapping("/api/internal/export")
@RequiredArgsConstructor
public class WarehouseExportController {

    private final PipelineRequestGuard guard;
    private final WarehouseExportService export;

    @GetMapping("/{dataset}")
    public ExportPage page(HttpServletRequest request, @PathVariable String dataset,
                           @RequestParam(defaultValue = "0") long after,
                           @RequestParam(defaultValue = "" + WarehouseExportService.DEFAULT_LIMIT) int limit) {
        guard.admit(request);
        ExportDataset source = ExportDataset.fromKey(dataset)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No dataset " + dataset));
        return export.page(source, after, limit);
    }
}
