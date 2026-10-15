package com.tailorcards.api.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Add and read only: deliberately not a JpaRepository, so there is no update or delete to call. */
public interface AdminAuditRepository extends Repository<AdminAuditEntry, Long> {

    AdminAuditEntry save(AdminAuditEntry entry);

    Page<AdminAuditEntry> findAllByOrderByIdDesc(Pageable pageable);

    Page<AdminAuditEntry> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, String entityId, Pageable pageable);

    Page<AdminAuditEntry> findByActionOrderByIdDesc(String action, Pageable pageable);

    long count();
}
