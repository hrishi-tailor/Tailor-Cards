package com.tailorcards.api.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tailorcards.api.security.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Records admin actions in the append-only audit log. Typical use around a change:
 * <pre>
 *   String before = audit.snapshot(service.get(id));   // serialised now, before the change can touch it
 *   Result after = service.update(id, request);
 *   audit.record("PRODUCT_UPDATE", "PRODUCT", id, before, after);
 * </pre>
 * Snapshots are JSON strings because JPA may update the very object that was read (open session in view).
 */
@Slf4j
@Service
public class AuditLog {

    private static final int MAX_JSON = 200_000;

    private final AdminAuditRepository entries;
    private final ObjectMapper json;
    private final int trustedHops;
    private final Clock clock;

    public AuditLog(AdminAuditRepository entries, @Value("${app.security.trusted-proxy-hops:1}") int trustedHops) {
        this.entries = entries;
        this.json = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.trustedHops = trustedHops;
        this.clock = Clock.systemUTC();
    }

    /** JSON of the current state, taken now; null for null. */
    public String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            String text = json.writeValueAsString(value);
            return text.length() > MAX_JSON ? text.substring(0, MAX_JSON) + "…(truncated)" : text;
        } catch (Exception e) {
            return "{\"unserializable\":\"" + value.getClass().getSimpleName() + "\"}";
        }
    }

    /** Snapshot that tolerates a failing lookup (e.g. the record doesn't exist): null instead of an exception. */
    public String snapshotOf(Supplier<Object> lookup) {
        try {
            return snapshot(lookup.get());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Appends one entry. Its own transaction, so it's kept even if the caller's later work rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Object entityId, Object before, Object after) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth == null ? "system" : auth.getName();
        String role = auth == null ? null : auth.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse(null);
        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : ClientAddress.of(request, trustedHops);
        String path = request == null ? null : truncate(request.getMethod() + " " + request.getRequestURI(), 200);
        entries.save(new AdminAuditEntry(clock.instant(), truncate(actor, 100), role, action, entityType,
                entityId == null ? null : truncate(String.valueOf(entityId), 100), snapshot(before), snapshot(after), ip, path));
        log.info("Audit: {} {} {} by {}", action, entityType, entityId, actor);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    Instant now() {
        return clock.instant();
    }
}
