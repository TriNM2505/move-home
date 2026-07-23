package vn.movehome.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.repository.AuditLogRepository;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogWriterTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void persistDelegatesToRepositorySaveAndFlush() {
        AuditLogWriter writer = new AuditLogWriter(auditLogRepository);
        AuditLog auditLog = AuditLog.builder()
                .actorEmail("admin@movehome.vn")
                .action("ORDER_ASSIGNED")
                .entityType("ORDER")
                .entityId("MH-001")
                .detail("CONFIRMED -> ASSIGNED")
                .createdAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
        when(auditLogRepository.saveAndFlush(auditLog)).thenReturn(auditLog);

        writer.persist(auditLog);

        verify(auditLogRepository).saveAndFlush(auditLog);
    }
}
