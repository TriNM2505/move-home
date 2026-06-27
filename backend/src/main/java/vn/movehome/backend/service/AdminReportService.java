package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.report.FinancialReportResponse;
import vn.movehome.backend.dto.admin.report.OperationsReportResponse;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int RANGE_MAX_DAYS = 365;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final Set<String> GROUP_BY_ALLOWED = Set.of("day", "week", "month");
    private static final Set<String> COMPARE_WITH_ALLOWED = Set.of("NONE", "PREVIOUS_PERIOD");

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;

    public FinancialReportResponse financialReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            String compareWith,
            String groupBy) {

        validateCompareGroup(compareWith, groupBy);
        ResolvedPeriod p = resolvePeriod(periodStart, periodEnd);
        String actualCompare = (compareWith != null) ? compareWith : "NONE";

        BigDecimal gbvTotal = orderRepository.sumCompletedTotalQuoteBetween(p.startInstant(), p.endInstant());

        List<OrderRepository.VehicleRevenueProjection> vehicleBreakdown =
                orderRepository.sumCompletedTotalQuoteByVehicleBetween(p.startInstant(), p.endInstant());
        Map<String, BigDecimal> breakdownMap = new LinkedHashMap<>();
        for (OrderRepository.VehicleRevenueProjection v : vehicleBreakdown) {
            breakdownMap.put(v.getVehicleType(), v.getTotalRevenue());
        }

        Instant instantFrom = toInstant(p.startInstant());
        Instant instantTo = toInstant(p.endInstant());
        BigDecimal platformFeeTotal = transactionRepository.sumAmountByTypeBetween(
                TransactionType.PLATFORM_FEE, instantFrom, instantTo);

        BigDecimal effectiveRate = (gbvTotal != null && gbvTotal.signum() > 0)
                ? platformFeeTotal.divide(gbvTotal, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal refundsRaw = transactionRepository.sumAmountByTypeBetween(
                TransactionType.REFUND, instantFrom, instantTo);
        BigDecimal damageRaw = transactionRepository.sumAmountByTypeBetween(
                TransactionType.DAMAGE_DEDUCTION, instantFrom, instantTo);
        BigDecimal refunds = refundsRaw.abs();
        BigDecimal damageRecovery = damageRaw.abs();

        BigDecimal contribution = platformFeeTotal.subtract(refunds);

        FinancialReportResponse.Compare compare = null;
        if ("PREVIOUS_PERIOD".equals(actualCompare)) {
            long periodDays = ChronoUnit.DAYS.between(p.startDate(), p.endDate());
            LocalDate prevEnd = p.startDate();
            LocalDate prevStart = prevEnd.minusDays(periodDays);
            OffsetDateTime prevStartOdt = prevStart.atStartOfDay(VN_ZONE).toOffsetDateTime();
            OffsetDateTime prevEndOdt = prevEnd.atStartOfDay(VN_ZONE).toOffsetDateTime();

            BigDecimal prevGbv = orderRepository.sumCompletedTotalQuoteBetween(prevStartOdt, prevEndOdt);
            BigDecimal prevFee = transactionRepository.sumAmountByTypeBetween(
                    TransactionType.PLATFORM_FEE, prevStartOdt.toInstant(), prevEndOdt.toInstant());
            BigDecimal prevRefunds = transactionRepository.sumAmountByTypeBetween(
                    TransactionType.REFUND, prevStartOdt.toInstant(), prevEndOdt.toInstant()).abs();
            BigDecimal prevContrib = prevFee.subtract(prevRefunds);

            compare = new FinancialReportResponse.Compare(
                    percentChange(prevGbv, gbvTotal),
                    percentChange(prevFee, platformFeeTotal),
                    percentChange(prevContrib, contribution));
        }

        List<FinancialReportResponse.TrendPoint> trend = List.of();
        List<FinancialReportResponse.DataQualityWarning> dataQuality = List.of();

        return new FinancialReportResponse(
                new FinancialReportResponse.Period(p.startDate(), p.endDate()),
                new FinancialReportResponse.GrossBookingValue(gbvTotal, breakdownMap),
                new FinancialReportResponse.PlatformFee(platformFeeTotal, effectiveRate),
                refunds,
                damageRecovery,
                contribution,
                compare,
                trend,
                dataQuality);
    }

    public OperationsReportResponse operationsReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            String compareWith,
            String groupBy) {

        validateCompareGroup(compareWith, groupBy);
        ResolvedPeriod p = resolvePeriod(periodStart, periodEnd);
        String actualCompare = (compareWith != null) ? compareWith : "NONE";

        long totalCreated = orderRepository.countCreatedOrdersBetween(p.startInstant(), p.endInstant());
        long completed = orderRepository.countCreatedOrdersByStatusBetween(
                p.startInstant(), p.endInstant(), "COMPLETED");
        long cancelled = orderRepository.countCreatedOrdersByStatusBetween(
                p.startInstant(), p.endInstant(), "CANCELLED");
        long inDispute = orderRepository.countCreatedOrdersByStatusBetween(
                p.startInstant(), p.endInstant(), "DISPUTED");
        long terminalEligible = completed + cancelled + inDispute;

        BigDecimal completionRate = (terminalEligible > 0)
                ? BigDecimal.valueOf(completed).divide(BigDecimal.valueOf(terminalEligible), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal disputeRate = (terminalEligible > 0)
                ? BigDecimal.valueOf(inDispute).divide(BigDecimal.valueOf(terminalEligible), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        OperationsReportResponse.OrderMetrics orderMetrics = new OperationsReportResponse.OrderMetrics(
                totalCreated, completed, cancelled, inDispute, terminalEligible, completionRate, disputeRate);

        BigDecimal avgOrderValue = orderRepository.averageCompletedOrderValueBetween(p.startInstant(), p.endInstant());
        BigDecimal avgDistance = orderRepository.averageDistanceKmForCreatedOrdersBetween(
                p.startInstant(), p.endInstant());

        long peakOrders = orderRepository.countPeakSurchargeOrdersBetween(p.startInstant(), p.endInstant());
        BigDecimal peakRate = (totalCreated > 0)
                ? BigDecimal.valueOf(peakOrders).divide(BigDecimal.valueOf(totalCreated), 4, RoundingMode.HALF_UP)
                : null;

        BigDecimal avgCompletionTime = null;

        List<OrderRepository.StatusCountProjection> statusList =
                orderRepository.countCreatedOrdersGroupByStatusBetween(p.startInstant(), p.endInstant());
        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (OrderRepository.StatusCountProjection s : statusList) {
            statusDistribution.put(s.getStatus(), s.getCount());
        }

        List<OrderRepository.OrderTrendProjection> trendData =
                orderRepository.findCompletionTrend(p.startInstant(), p.endInstant());
        List<OperationsReportResponse.CompletionTrendPoint> trend = trendData.stream()
                .map(t -> new OperationsReportResponse.CompletionTrendPoint(
                        t.getBucket(), t.getCreatedCount(), t.getCompletedCount()))
                .toList();

        OperationsReportResponse.Compare compare = null;
        if ("PREVIOUS_PERIOD".equals(actualCompare)) {
            long periodDays = ChronoUnit.DAYS.between(p.startDate(), p.endDate());
            LocalDate prevEnd = p.startDate();
            LocalDate prevStart = prevEnd.minusDays(periodDays);
            OffsetDateTime prevStartOdt = prevStart.atStartOfDay(VN_ZONE).toOffsetDateTime();
            OffsetDateTime prevEndOdt = prevEnd.atStartOfDay(VN_ZONE).toOffsetDateTime();

            long prevCreated = orderRepository.countCreatedOrdersBetween(prevStartOdt, prevEndOdt);
            long prevCompleted = orderRepository.countCreatedOrdersByStatusBetween(
                    prevStartOdt, prevEndOdt, "COMPLETED");
            long prevCancelled = orderRepository.countCreatedOrdersByStatusBetween(
                    prevStartOdt, prevEndOdt, "CANCELLED");
            long prevDispute = orderRepository.countCreatedOrdersByStatusBetween(
                    prevStartOdt, prevEndOdt, "DISPUTED");
            long prevTerminal = prevCompleted + prevCancelled + prevDispute;
            BigDecimal prevCompletionRate = (prevTerminal > 0)
                    ? BigDecimal.valueOf(prevCompleted)
                            .divide(BigDecimal.valueOf(prevTerminal), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            compare = new OperationsReportResponse.Compare(
                    percentChange(BigDecimal.valueOf(prevCreated), BigDecimal.valueOf(totalCreated)),
                    percentChange(prevCompletionRate, completionRate));
        }

        List<FinancialReportResponse.DataQualityWarning> dataQuality = List.of(
                new FinancialReportResponse.DataQualityWarning(
                        "average_completion_time_minutes", "MISSING_STARTED_AT_FIELD"),
                new FinancialReportResponse.DataQualityWarning(
                        "peak_hour_orders_rate", "PROXY_PEAK_SURCHARGE_BASED"));

        return new OperationsReportResponse(
                new FinancialReportResponse.Period(p.startDate(), p.endDate()),
                orderMetrics,
                avgOrderValue,
                avgDistance,
                peakRate,
                avgCompletionTime,
                statusDistribution,
                trend,
                compare,
                dataQuality);
    }

    private ResolvedPeriod resolvePeriod(LocalDate periodStart, LocalDate periodEnd) {
        LocalDate today = LocalDate.now(VN_ZONE);
        LocalDate end = (periodEnd != null) ? periodEnd : today.plusDays(1);
        LocalDate start = (periodStart != null) ? periodStart : end.minusDays(DEFAULT_RANGE_DAYS);

        if (start.isAfter(today) || !start.isBefore(end)) {
            throw invalidDateRange();
        }
        if (ChronoUnit.DAYS.between(start, end) > RANGE_MAX_DAYS) {
            throw invalidDateRange();
        }

        OffsetDateTime startOdt = start.atStartOfDay(VN_ZONE).toOffsetDateTime();
        OffsetDateTime endOdt = end.atStartOfDay(VN_ZONE).toOffsetDateTime();
        return new ResolvedPeriod(start, end, startOdt, endOdt);
    }

    private void validateCompareGroup(String compareWith, String groupBy) {
        if (compareWith != null && !COMPARE_WITH_ALLOWED.contains(compareWith)) {
            throw invalidCompareOption();
        }
        if (groupBy != null && !GROUP_BY_ALLOWED.contains(groupBy)) {
            throw invalidGrouping();
        }
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }

    private BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private ResponseStatusException invalidDateRange() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_DATE_RANGE|Khoang ngay khong hop le.");
    }

    private ResponseStatusException invalidCompareOption() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_COMPARE_OPTION|Option compare khong hop le.");
    }

    private ResponseStatusException invalidGrouping() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_GROUPING|Cach nhom du lieu khong hop le.");
    }

    private record ResolvedPeriod(
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime startInstant,
            OffsetDateTime endInstant) {
    }
}
