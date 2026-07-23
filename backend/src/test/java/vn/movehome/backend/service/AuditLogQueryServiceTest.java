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
    void specificationAppliesAllFourFiltersWhenInvoked() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        PageRequest pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findAuditLogs(" ORDER_UPDATED ", " SERVICE_ORDER ", from, to, pageable);

        ArgumentCaptor<Specification<AuditLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(auditLogRepository).findAll(specCaptor.capture(), eq(pageable));

        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Root<AuditLog> root = org.mockito.Mockito.mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder cb = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        jakarta.persistence.criteria.Path<Instant> path = org.mockito.Mockito.mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Predicate predicate = org.mockito.Mockito.mock(jakarta.persistence.criteria.Predicate.class);

        org.mockito.Mockito.doReturn(path).when(root).get(org.mockito.ArgumentMatchers.anyString());
        when(cb.conjunction()).thenReturn(predicate);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<Instant>>any(), org.mockito.ArgumentMatchers.eq(from)))
                .thenReturn(predicate);
        when(cb.lessThanOrEqualTo(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<Instant>>any(), org.mockito.ArgumentMatchers.eq(to)))
                .thenReturn(predicate);
        when(cb.and(any(), any())).thenReturn(predicate);

        jakarta.persistence.criteria.Predicate result = specCaptor.getValue().toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).equal(path, "ORDER_UPDATED");
        verify(cb).equal(path, "SERVICE_ORDER");
        verify(cb).greaterThanOrEqualTo(path, from);
        verify(cb).lessThanOrEqualTo(path, to);
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
    void findAuditLogsSkipsAllOptionalFiltersWhenBlankOrNull() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        // action null, entityType blank, from/to null -> tat ca 4 nhanh "if" deu la false.
        Page<AuditLogResponse> result = service.findAuditLogs(null, "  ", null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        ArgumentCaptor<Specification<AuditLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(auditLogRepository).findAll(specCaptor.capture(), eq(pageable));
        assertThat(specCaptor.getValue()).isNotNull();
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
