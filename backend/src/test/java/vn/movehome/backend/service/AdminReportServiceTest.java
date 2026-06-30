package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.report.CustomersReportResponse;
import vn.movehome.backend.dto.admin.report.DriversReportResponse;
import vn.movehome.backend.dto.admin.report.FinancialReportResponse;
import vn.movehome.backend.dto.admin.report.OperationsReportResponse;
import vn.movehome.backend.dto.admin.report.PeakHoursReportResponse;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRatingRepository;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private AdminReportService service;

    @BeforeEach
    void setUp() {
        service = new AdminReportService(orderRepository, transactionRepository, userRepository, orderRatingRepository);
    }

    @Test
    void financialReportCalculatesFeeRateRefundsContributionAndPreviousPeriodCompare() {
        when(orderRepository.sumCompletedTotalQuoteBetween(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("1000000"), new BigDecimal("500000"));
        when(orderRepository.sumCompletedTotalQuoteByVehicleBetween(any(), any())).thenReturn(List.of(
                vehicleRevenue("TRUCK_500KG", new BigDecimal("400000")),
                vehicleRevenue("TRUCK_1000KG", new BigDecimal("600000"))));
        when(transactionRepository.sumAmountByTypeBetween(
                eq(TransactionType.PLATFORM_FEE), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("300000"), new BigDecimal("100000"));
        when(transactionRepository.sumAmountByTypeBetween(
                eq(TransactionType.REFUND), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("-50000"), new BigDecimal("-20000"));
        when(transactionRepository.sumAmountByTypeBetween(
                eq(TransactionType.DAMAGE_DEDUCTION), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("-20000"));

        FinancialReportResponse report = service.financialReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                "PREVIOUS_PERIOD",
                "day");

        assertThat(report.grossBookingValue().total()).isEqualByComparingTo("1000000");
        assertThat(report.grossBookingValue().breakdownByVehicle())
                .containsEntry("TRUCK_500KG", new BigDecimal("400000"))
                .containsEntry("TRUCK_1000KG", new BigDecimal("600000"));
        assertThat(report.platformFee().total()).isEqualByComparingTo("300000");
        assertThat(report.platformFee().effectiveRate()).isEqualByComparingTo("0.3000");
        assertThat(report.refunds()).isEqualByComparingTo("50000");
        assertThat(report.damageRecovery()).isEqualByComparingTo("20000");
        assertThat(report.managementNetContribution()).isEqualByComparingTo("250000");
        assertThat(report.compare().gbvChange()).isEqualByComparingTo("100.00");
        assertThat(report.compare().feeChange()).isEqualByComparingTo("200.00");
        assertThat(report.compare().contribChange()).isEqualByComparingTo("212.50");
    }

    @Test
    void operationsReportCalculatesRatesStatusDistributionTrendAndCompare() {
        when(orderRepository.countCreatedOrdersBetween(any(), any())).thenReturn(10L, 5L);
        when(orderRepository.countCreatedOrdersByStatusBetween(any(), any(), eq("COMPLETED"))).thenReturn(6L, 2L);
        when(orderRepository.countCreatedOrdersByStatusBetween(any(), any(), eq("CANCELLED"))).thenReturn(1L, 2L);
        when(orderRepository.countCreatedOrdersByStatusBetween(any(), any(), eq("DISPUTED"))).thenReturn(1L, 1L);
        when(orderRepository.averageCompletedOrderValueBetween(any(), any())).thenReturn(new BigDecimal("250000"));
        when(orderRepository.averageDistanceKmForCreatedOrdersBetween(any(), any())).thenReturn(new BigDecimal("8.50"));
        when(orderRepository.countPeakSurchargeOrdersBetween(any(), any())).thenReturn(2L);
        when(orderRepository.countCreatedOrdersGroupByStatusBetween(any(), any())).thenReturn(List.of(
                statusCount("COMPLETED", 6L),
                statusCount("CANCELLED", 1L)));
        when(orderRepository.findCompletionTrend(any(), any())).thenReturn(List.of(
                orderTrend("2026-06-01", 4L, 3L)));

        OperationsReportResponse report = service.operationsReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                "PREVIOUS_PERIOD",
                "week");

        assertThat(report.orders().totalCreated()).isEqualTo(10);
        assertThat(report.orders().terminalEligible()).isEqualTo(8);
        assertThat(report.orders().completionRate()).isEqualByComparingTo("0.7500");
        assertThat(report.orders().disputeRate()).isEqualByComparingTo("0.1250");
        assertThat(report.peakHourOrdersRate()).isEqualByComparingTo("0.2000");
        assertThat(report.statusDistribution()).containsEntry("COMPLETED", 6L);
        assertThat(report.completionTrend()).singleElement().satisfies(point -> {
            assertThat(point.bucket()).isEqualTo("2026-06-01");
            assertThat(point.completed()).isEqualTo(3L);
        });
        assertThat(report.compare().totalCreatedChange()).isEqualByComparingTo("100.00");
        assertThat(report.compare().completionRateChange()).isEqualByComparingTo("87.50");
        assertThat(report.dataQuality()).extracting(FinancialReportResponse.DataQualityWarning::code)
                .contains("MISSING_STARTED_AT_FIELD", "PROXY_PEAK_SURCHARGE_BASED");
    }

    @Test
    void driversReportCalculatesTopEarnersRatingsAndChurnProxy() {
        UUID driverId = UUID.randomUUID();
        when(userRepository.countByRoleAndDeletedAtIsNullAndCreatedAtBefore(
                eq(UserRole.DRIVER), any(Instant.class))).thenReturn(20L);
        when(userRepository.countByRoleAndStatusAndDeletedAtIsNullAndCreatedAtBefore(
                eq(UserRole.DRIVER), eq(UserStatus.ACTIVE), any(Instant.class))).thenReturn(12L);
        when(transactionRepository.findTopEarnersByDriverEarning(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(topEarner(driverId, "Driver Top", new BigDecimal("2500000"), 9L)));
        when(orderRatingRepository.countRatingsByStarBetween(any(), any())).thenReturn(List.of(
                ratingCount(4, 2L),
                ratingCount(5, 3L),
                ratingCount(null, 10L)));
        when(orderRatingRepository.averageRatingBetween(any(), any()))
                .thenReturn(java.util.Optional.of(new BigDecimal("4.666")));
        when(orderRepository.countChurnedDrivers(any(), any())).thenReturn(4L);

        DriversReportResponse report = service.driversReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                "NONE");

        assertThat(report.totalDriversAtPeriodEnd()).isEqualTo(20);
        assertThat(report.activeDriversAtPeriodEnd()).isEqualTo(12);
        assertThat(report.topEarners()).singleElement().satisfies(item -> {
            assertThat(item.driverId()).isEqualTo(driverId);
            assertThat(item.totalEarning()).isEqualByComparingTo("2500000");
            assertThat(item.completedOrders()).isEqualTo(9);
        });
        assertThat(report.ratingDistribution().star4()).isEqualTo(2);
        assertThat(report.ratingDistribution().star5()).isEqualTo(3);
        assertThat(report.ratingDistribution().total()).isEqualTo(5);
        assertThat(report.averageRatingOverall()).isEqualByComparingTo("4.67");
        assertThat(report.operationalChurnProxyCount()).isEqualTo(4);
    }

    @Test
    void customersReportCalculatesActiveUsersSpendRetentionAndCompare() {
        UUID customerId = UUID.randomUUID();
        when(userRepository.countByRoleAndDeletedAtIsNullAndCreatedAtBefore(
                eq(UserRole.CUSTOMER), any(Instant.class))).thenReturn(100L);
        when(orderRepository.calculateDauAverage(any(), any())).thenReturn(new BigDecimal("7.666"));
        when(orderRepository.countMauBetween(any(), any())).thenReturn(20L);
        when(orderRepository.calculateRetentionRate30d(any(), any(), any()))
                .thenReturn(new BigDecimal("0.33333"), new BigDecimal("0.2500"));
        when(orderRepository.findTopSpendersByCompletedOrderTotal(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(topSpender(customerId, "Customer Top", 3L, new BigDecimal("1500000"))));
        when(orderRepository.sumCompletedTotalQuoteBetween(any(), any())).thenReturn(new BigDecimal("4000000"));
        when(userRepository.countNewCustomersBetween(any(), any())).thenReturn(12L, 8L);

        CustomersReportResponse report = service.customersReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                "PREVIOUS_PERIOD");

        assertThat(report.totalCustomersAtPeriodEnd()).isEqualTo(100);
        assertThat(report.activeUsers().dauAverage()).isEqualByComparingTo("7.67");
        assertThat(report.activeUsers().mau()).isEqualTo(20);
        assertThat(report.retentionRate30d()).isEqualByComparingTo("0.3333");
        assertThat(report.topSpenders()).singleElement().satisfies(item -> {
            assertThat(item.customerId()).isEqualTo(customerId);
            assertThat(item.completedOrders()).isEqualTo(3);
        });
        assertThat(report.averageSpendPerPayingCustomer()).isEqualByComparingTo("200000");
        assertThat(report.newCustomersInPeriod()).isEqualTo(12);
        assertThat(report.compare().newCustomersChange()).isEqualByComparingTo("50.00");
        assertThat(report.compare().retentionChange()).isEqualByComparingTo("33.32");
    }

    @Test
    void peakHoursReportFillsFullWeekHeatmapAndFindsTopCell() {
        when(orderRepository.countOrdersByScheduledWeekdayHour(any(), any())).thenReturn(List.of(
                peakHour(2, 9, 5L, 4L),
                peakHour(6, 18, 8L, 7L)));
        when(orderRepository.countMissingScheduleBetween(any(), any())).thenReturn(3L);

        PeakHoursReportResponse report = service.peakHoursReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15));

        assertThat(report.heatmap()).hasSize(168);
        assertThat(report.peakHourInsight().topWeekday()).isEqualTo(6);
        assertThat(report.peakHourInsight().topHour()).isEqualTo(18);
        assertThat(report.peakHourInsight().topCellOrderCount()).isEqualTo(8L);
        assertThat(report.excludedMissingSchedule()).isEqualTo(3);
        assertThat(report.heatmap()).anySatisfy(cell -> {
            assertThat(cell.weekday()).isEqualTo(2);
            assertThat(cell.hour()).isEqualTo(9);
            assertThat(cell.orderCount()).isEqualTo(5);
            assertThat(cell.completedCount()).isEqualTo(4);
        });
    }

    @Test
    void reportRejectsInvalidOptionsAndDateRange() {
        assertThatThrownBy(() -> service.financialReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                "LAST_YEAR",
                "day"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_COMPARE_OPTION");
        assertThatThrownBy(() -> service.operationsReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                "NONE",
                "quarter"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_GROUPING");
        assertThatThrownBy(() -> service.peakHoursReport(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_DATE_RANGE");
    }

    private OrderRepository.VehicleRevenueProjection vehicleRevenue(String vehicleType, BigDecimal totalRevenue) {
        return new OrderRepository.VehicleRevenueProjection() {
            @Override
            public String getVehicleType() {
                return vehicleType;
            }

            @Override
            public BigDecimal getTotalRevenue() {
                return totalRevenue;
            }
        };
    }

    private OrderRepository.StatusCountProjection statusCount(String status, Long count) {
        return new OrderRepository.StatusCountProjection() {
            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private OrderRepository.OrderTrendProjection orderTrend(String bucket, Long created, Long completed) {
        return new OrderRepository.OrderTrendProjection() {
            @Override
            public String getBucket() {
                return bucket;
            }

            @Override
            public Long getCreatedCount() {
                return created;
            }

            @Override
            public Long getCompletedCount() {
                return completed;
            }
        };
    }

    private TransactionRepository.TopEarnerProjection topEarner(
            UUID driverId, String fullName, BigDecimal totalEarning, Long completedOrders) {
        return new TransactionRepository.TopEarnerProjection() {
            @Override
            public UUID getDriverId() {
                return driverId;
            }

            @Override
            public String getFullName() {
                return fullName;
            }

            @Override
            public BigDecimal getTotalEarning() {
                return totalEarning;
            }

            @Override
            public Long getCompletedOrders() {
                return completedOrders;
            }
        };
    }

    private OrderRatingRepository.RatingStarCount ratingCount(Integer star, Long count) {
        return new OrderRatingRepository.RatingStarCount() {
            @Override
            public Integer getStar() {
                return star;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private OrderRepository.TopSpenderProjection topSpender(
            UUID customerId, String fullName, Long completedOrders, BigDecimal totalSpent) {
        return new OrderRepository.TopSpenderProjection() {
            @Override
            public UUID getCustomerId() {
                return customerId;
            }

            @Override
            public String getFullName() {
                return fullName;
            }

            @Override
            public Long getCompletedOrders() {
                return completedOrders;
            }

            @Override
            public BigDecimal getTotalSpent() {
                return totalSpent;
            }
        };
    }

    private OrderRepository.PeakHourProjection peakHour(Integer weekday, Integer hour, Long orders, Long completed) {
        return new OrderRepository.PeakHourProjection() {
            @Override
            public Integer getWeekday() {
                return weekday;
            }

            @Override
            public Integer getHour() {
                return hour;
            }

            @Override
            public Long getOrderCount() {
                return orders;
            }

            @Override
            public Long getCompletedCount() {
                return completed;
            }
        };
    }
}
