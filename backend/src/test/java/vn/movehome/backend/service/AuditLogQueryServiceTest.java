package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.AuditLogResponse;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.repository.AuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogQueryService(auditLogRepository);
    }

    @Test
    void findAuditLogsBuildsSpecificationAndMapsEntitiesToResponses() {
        Instant createdAt = Instant.parse("2026-06-15T10:15:30Z");
        AuditLog log = AuditLog.builder()
                .actorEmail("admin@movehome.vn")
                .action("ORDER_UPDATED")
                .entityType("SERVICE_ORDER")
                .entityId("MH-001")
                .detail("status changed")
                .createdAt(createdAt)
                .build();
        PageRequest pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogResponse> result = service.findAuditLogs(
                " ORDER_UPDATED ", " SERVICE_ORDER ",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T23:59:59Z"),
                pageable);

        assertThat(result.getContent()).hasSize(1);
        AuditLogResponse response = result.getContent().get(0);
        assertThat(response.actorEmail()).isEqualTo("admin@movehome.vn");
        assertThat(response.action()).isEqualTo("ORDER_UPDATED");
        assertThat(response.entityType()).isEqualTo("SERVICE_ORDER");
        assertThat(response.entityId()).isEqualTo("MH-001");
        assertThat(response.detail()).isEqualTo("status changed");
        assertThat(response.createdAt()).isEqualTo(createdAt);

        ArgumentCaptor<Specification<AuditLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(auditLogRepository).findAll(specCaptor.capture(), eq(pageable));
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    void findAuditLogsRejectsInvertedTimeRange() {
        assertThatThrownBy(() -> service.findAuditLogs(
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_TIME_RANGE");
    }

    @Test
    void findAuditLogsReturnsEmptyPageWhenRepositoryHasNoMatches() {
        PageRequest pageable = PageRequest.of(2, 10);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<AuditLogResponse> result = service.findAuditLogs(
                "UNKNOWN_ACTION",
                "SERVICE_ORDER",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T23:59:59Z"),
                pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        verify(auditLogRepository).findAll(any(Specification.class), eq(pageable));
    }
}
