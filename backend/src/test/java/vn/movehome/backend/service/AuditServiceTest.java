package vn.movehome.backend.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.movehome.backend.entity.AuditLog;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AuditServiceTest {

    private final AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
    private final AuditService auditService = new AuditService(auditLogWriter);

    @Test
    void logOncePersistsExactlyOneAuditRow() {
        UUID actorId = UUID.randomUUID();

        auditService.log(actorId, "admin@movehome.vn", "ORDER_ASSIGNED",
                "ORDER", "MH-001", "CONFIRMED -> ASSIGNED");

        ArgumentCaptor<AuditLog> rowCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogWriter).persist(rowCaptor.capture());
        verifyNoMoreInteractions(auditLogWriter);

        AuditLog row = rowCaptor.getValue();
        assertThat(row.getActorId()).isEqualTo(actorId);
        assertThat(row.getActorEmail()).isEqualTo("admin@movehome.vn");
        assertThat(row.getAction()).isEqualTo("ORDER_ASSIGNED");
        assertThat(row.getEntityType()).isEqualTo("ORDER");
        assertThat(row.getEntityId()).isEqualTo("MH-001");
        assertThat(row.getDetail()).isEqualTo("CONFIRMED -> ASSIGNED");
    }

    @Test
    void auditFailureIsOnlyWarnedAndDoesNotEscape() {
        doThrow(new RuntimeException("database unavailable"))
                .when(auditLogWriter).persist(org.mockito.ArgumentMatchers.any(AuditLog.class));

        assertThatCode(() -> auditService.log(null, null, "SYSTEM_EVENT", null, null, null))
                .doesNotThrowAnyException();
    }
}
