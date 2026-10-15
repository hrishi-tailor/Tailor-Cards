package com.tailorcards.api.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of the audit log (ADMIN only; the log has no write or delete endpoint). */
@RestController
@RequestMapping("/api/admin/audit-log")
@Tag(name = "Admin audit log")
public class AuditLogController {

    private final AdminAuditRepository entries;

    public AuditLogController(AdminAuditRepository entries) {
        this.entries = entries;
    }

    public record AuditPage(java.util.List<AdminAuditEntry> items, int page, int size, long total) {}

    @GetMapping
    @Operation(summary = "Admin actions, newest first; filter by entityType+entityId or by action")
    public AuditPage list(@RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) String entityId,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)));
        Page<AdminAuditEntry> result = entityType != null && entityId != null
                ? entries.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId, request)
                : action != null ? entries.findByActionOrderByIdDesc(action, request)
                : entries.findAllByOrderByIdDesc(request);
        return new AuditPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
