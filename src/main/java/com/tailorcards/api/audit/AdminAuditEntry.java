package com.tailorcards.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One admin action: who, what, when, and the record before and after (JSON). Append-only: Hibernate never updates
 * an @Immutable entity, the repository has no update or delete, and in PostgreSQL a trigger rejects UPDATE, DELETE
 * and TRUNCATE on the table.
 */
@Entity
@Immutable
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor
public class AdminAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 100, updatable = false)
    private String actor;

    @Column(name = "actor_role", length = 20, updatable = false)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "entity_type", length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 100, updatable = false)
    private String entityId;

    @Column(name = "before_json", columnDefinition = "TEXT", updatable = false)
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT", updatable = false)
    private String afterJson;

    @Column(name = "ip", length = 64, updatable = false)
    private String ip;

    @Column(name = "request_path", length = 200, updatable = false)
    private String requestPath;

    AdminAuditEntry(Instant occurredAt, String actor, String actorRole, String action, String entityType, String entityId,
                    String beforeJson, String afterJson, String ip, String requestPath) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.ip = ip;
        this.requestPath = requestPath;
    }
}
