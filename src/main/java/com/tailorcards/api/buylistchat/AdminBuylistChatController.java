package com.tailorcards.api.buylistchat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/buylist-chat")
@Tag(name = "Admin - Buylist Chat", description = "Admin controls for the AI buylist chat")
@SecurityRequirement(name = "basicAuth")
public class AdminBuylistChatController {

    public record ResetRequest(String email) {}

    private final BuylistChatSubmissionService submissions;
    private final com.tailorcards.api.audit.AuditLog audit;

    public AdminBuylistChatController(BuylistChatSubmissionService submissions, com.tailorcards.api.audit.AuditLog audit) {
        this.audit = audit;
        this.submissions = submissions;
    }

    @PostMapping("/daily-limit/reset")
    @Operation(summary = "Let a customer submit again today", description = "Releases today's one-per-day slot for the email; the submission itself is kept.")
    public Map<String, Object> resetDailyLimit(@RequestBody ResetRequest request) {
        int released = submissions.resetDailyLimit(request.email());
        audit.record("BUYLIST_DAILY_LIMIT_RESET", "CUSTOMER_EMAIL", request.email(), null, Map.of("released", released));
        return Map.of("released", released);
    }
}
