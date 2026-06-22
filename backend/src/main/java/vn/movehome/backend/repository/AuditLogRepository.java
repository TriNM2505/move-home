package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.movehome.backend.entity.AuditLog;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
