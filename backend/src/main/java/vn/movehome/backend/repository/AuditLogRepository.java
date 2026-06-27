package vn.movehome.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.dto.admin.detail.AuditLogItem;
import vn.movehome.backend.entity.AuditLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Query("""
            select new vn.movehome.backend.dto.admin.detail.AuditLogItem(
                a.id,
                a.action,
                a.actorId,
                a.actorEmail,
                a.entityType,
                a.entityId,
                a.detail,
                cast(a.createdAt as java.time.OffsetDateTime)
            )
            from AuditLog a
            where a.entityType = :entityType
              and a.entityId = :entityId
              and (coalesce(:eventType, '') = '' or a.action = :eventType)
              and (cast(:from as java.time.OffsetDateTime) is null
                   or cast(a.createdAt as java.time.OffsetDateTime) >= :from)
              and (cast(:to as java.time.OffsetDateTime) is null
                   or cast(a.createdAt as java.time.OffsetDateTime) < :to)
            """)
    Page<AuditLogItem> findAdminEntityAuditLog(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("eventType") String eventType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
