package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.movehome.backend.dto.admin.finance.AdminTransactionResponse;
import vn.movehome.backend.service.AdminTransactionService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTransactionControllerTest {

    private final AdminTransactionService service = mock(AdminTransactionService.class);
    private final AdminTransactionController controller = new AdminTransactionController(service);

    @Test
    void findTransactionsUsesDefaultSizeAndPrimaryFromToUserIdWhenPresent() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant dateFrom = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-02T00:00:00Z");
        Instant dateTo = Instant.parse("2026-01-02T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID userIdSnake = UUID.randomUUID();
        Page<AdminTransactionResponse> expected = new PageImpl<>(List.of());
        when(service.defaultPageSize()).thenReturn(20);
        when(service.findTransactions("ALL", from, to, userId, 0, 20)).thenReturn(expected);

        Page<AdminTransactionResponse> actual = controller.findTransactions(
                0, null, "ALL", from, dateFrom, to, dateTo, userId, userIdSnake);

        assertThat(actual).isEqualTo(expected);
        verify(service).findTransactions("ALL", from, to, userId, 0, 20);
    }

    @Test
    void findTransactionsFallsBackToSnakeCaseParamsWhenPrimaryMissing() {
        Instant dateFrom = Instant.parse("2026-01-01T00:00:00Z");
        Instant dateTo = Instant.parse("2026-01-02T00:00:00Z");
        UUID userIdSnake = UUID.randomUUID();
        Page<AdminTransactionResponse> expected = new PageImpl<>(List.of());
        when(service.findTransactions("WITHDRAWAL", dateFrom, dateTo, userIdSnake, 2, 50)).thenReturn(expected);

        Page<AdminTransactionResponse> actual = controller.findTransactions(
                2, 50, "WITHDRAWAL", null, dateFrom, null, dateTo, null, userIdSnake);

        assertThat(actual).isEqualTo(expected);
        verify(service).findTransactions("WITHDRAWAL", dateFrom, dateTo, userIdSnake, 2, 50);
    }
}
