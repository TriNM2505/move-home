package vn.movehome.backend.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import vn.movehome.backend.dto.admin.report.CustomersReportResponse;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.order.OrderRatingRepository;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.LoginEventRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRatingRepository orderRatingRepository;

    @Mock
    private LoginEventRepository loginEventRepository;

    private AdminReportService adminReportService;

    @BeforeEach
    void setUp() {
        adminReportService = new AdminReportService(
                orderRepository,
                transactionRepository,
                userRepository,
                orderRatingRepository,
                loginEventRepository);
    }

    @Test
    void customersReportUsesLoginEventsForActivityAndRetentionWithoutProxyWarnings() {
        when(userRepository.countByRoleAndDeletedAtIsNullAndCreatedAtBefore(eq(UserRole.CUSTOMER), any()))
                .thenReturn(12L);
        when(loginEventRepository.calculateCustomerDauAverage(any(), any()))
                .thenReturn(new BigDecimal("2.345"));
        when(loginEventRepository.countCustomerMauBetween(any(), any()))
                .thenReturn(9L);
        when(loginEventRepository.calculateCustomerRetentionRate30d(any(), any(), any()))
                .thenReturn(new BigDecimal("0.45678"));
        when(orderRepository.findTopSpendersByCompletedOrderTotal(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.sumCompletedTotalQuoteBetween(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(userRepository.countNewCustomersBetween(any(), any()))
                .thenReturn(3L);

        CustomersReportResponse response = adminReportService.customersReport(
                java.time.LocalDate.of(2026, 6, 1),
                java.time.LocalDate.of(2026, 7, 1),
                "NONE");

        assertThat(response.activeUsers().dauAverage()).isEqualByComparingTo("2.35");
        assertThat(response.activeUsers().mau()).isEqualTo(9L);
        assertThat(response.retentionRate30d()).isEqualByComparingTo("0.4568");
        assertThat(response.dataQuality()).isEmpty();

        verify(loginEventRepository).calculateCustomerDauAverage(any(), any());
        verify(loginEventRepository).countCustomerMauBetween(any(), any());
        verify(loginEventRepository).calculateCustomerRetentionRate30d(any(), any(), any());
        verify(orderRepository, never()).calculateDauAverage(any(OffsetDateTime.class), any(OffsetDateTime.class));
        verify(orderRepository, never()).countMauBetween(any(OffsetDateTime.class), any(OffsetDateTime.class));
        verify(orderRepository, never()).calculateRetentionRate30d(
                any(OffsetDateTime.class), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    void customersReportJsonIncludesRetentionRateWhenNull() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        CustomersReportResponse response = new CustomersReportResponse(
                new vn.movehome.backend.dto.admin.report.FinancialReportResponse.Period(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)),
                12L,
                new CustomersReportResponse.ActiveUsers(BigDecimal.ONE, 1L),
                null,
                List.of(),
                BigDecimal.ZERO,
                0L,
                null,
                List.of());

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"retention_rate_30d\":null");
        assertThat(json).contains("\"data_quality\":[]");
    }
}
