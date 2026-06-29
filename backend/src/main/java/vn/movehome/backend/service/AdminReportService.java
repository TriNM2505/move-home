package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.movehome.backend.repository.LoginEventRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int TOP_N = 10;
    private static final int CHURN_INACTIVE_DAYS = 14;
    private static final Set<String> GROUP_BY_ALLOWED = Set.of("day", "week", "month");
    private static final Set<String> COMPARE_WITH_ALLOWED = Set.of("NONE", "PREVIOUS_PERIOD");

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final OrderRatingRepository orderRatingRepository;
    private final LoginEventRepository loginEventRepository;

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

    public DriversReportResponse driversReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            String compareWith) {

        validateCompareGroup(compareWith, null);
        ResolvedPeriod p = resolvePeriod(periodStart, periodEnd);

        long totalDrivers = userRepository.countByRoleAndDeletedAtIsNullAndCreatedAtBefore(
                UserRole.DRIVER, toInstant(p.endInstant()));
        long activeDrivers = userRepository.countByRoleAndStatusAndDeletedAtIsNullAndCreatedAtBefore(
                UserRole.DRIVER, UserStatus.ACTIVE, toInstant(p.endInstant()));

        BigDecimal onlineRatioAverage = null;

        Instant instantFrom = toInstant(p.startInstant());
        Instant instantTo = toInstant(p.endInstant());
        List<TransactionRepository.TopEarnerProjection> topEarnersRaw =
                transactionRepository.findTopEarnersByDriverEarning(
                        instantFrom, instantTo, PageRequest.of(0, TOP_N));
        List<DriversReportResponse.TopEarnerItem> topEarners = topEarnersRaw.stream()
                .map(t -> new DriversReportResponse.TopEarnerItem(
                        t.getDriverId(), t.getFullName(), t.getTotalEarning(), t.getCompletedOrders()))
                .toList();

        List<OrderRatingRepository.RatingStarCount> ratingCounts =
                orderRatingRepository.countRatingsByStarBetween(p.startInstant(), p.endInstant());
        long s1 = 0;
        long s2 = 0;
        long s3 = 0;
        long s4 = 0;
        long s5 = 0;
        for (OrderRatingRepository.RatingStarCount c : ratingCounts) {
            Integer star = c.getStar();
            long count = c.getCount() != null ? c.getCount() : 0;
            if (star == null) {
                continue;
            }
            switch (star) {
                case 1 -> s1 = count;
                case 2 -> s2 = count;
                case 3 -> s3 = count;
                case 4 -> s4 = count;
                case 5 -> s5 = count;
                default -> {
                }
            }
        }
        long totalRatings = s1 + s2 + s3 + s4 + s5;
        DriversReportResponse.RatingDistribution ratingDist =
                new DriversReportResponse.RatingDistribution(s1, s2, s3, s4, s5, totalRatings);

        BigDecimal averageRatingOverall = orderRatingRepository
                .averageRatingBetween(p.startInstant(), p.endInstant())
                .map(avg -> avg.setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);

        OffsetDateTime churnFrom = p.endInstant().minusDays(CHURN_INACTIVE_DAYS);
        Long churnCount = orderRepository.countChurnedDrivers(churnFrom, p.endInstant());

        List<FinancialReportResponse.DataQualityWarning> dataQuality = List.of(
                new FinancialReportResponse.DataQualityWarning(
                        "online_ratio_average", "MISSING_ONLINE_INTERVALS"),
                new FinancialReportResponse.DataQualityWarning(
                        "operational_churn_proxy_count", "PROXY_ORDER_BASED_CHURN"));

        return new DriversReportResponse(
                new FinancialReportResponse.Period(p.startDate(), p.endDate()),
                totalDrivers,
                activeDrivers,
                onlineRatioAverage,
                topEarners,
                ratingDist,
                averageRatingOverall,
                churnCount,
                dataQuality);
    }

    public CustomersReportResponse customersReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            String compareWith) {

        validateCompareGroup(compareWith, null);
        ResolvedPeriod p = resolvePeriod(periodStart, periodEnd);
        String actualCompare = (compareWith != null) ? compareWith : "NONE";

        long totalCustomers = userRepository.countByRoleAndDeletedAtIsNullAndCreatedAtBefore(
                UserRole.CUSTOMER, toInstant(p.endInstant()));

        BigDecimal dauAverage = loginEventRepository.calculateCustomerDauAverage(p.startInstant(), p.endInstant());
        if (dauAverage == null) {
            dauAverage = BigDecimal.ZERO;
        } else {
            dauAverage = dauAverage.setScale(2, RoundingMode.HALF_UP);
        }
        OffsetDateTime mauStart = p.endInstant().minusDays(30);
        long mau = loginEventRepository.countCustomerMauBetween(mauStart, p.endInstant());
        CustomersReportResponse.ActiveUsers activeUsers =
                new CustomersReportResponse.ActiveUsers(dauAverage, mau);

        OffsetDateTime cohortStart = p.startInstant().minusDays(30);
        BigDecimal retentionRate = loginEventRepository.calculateCustomerRetentionRate30d(
                cohortStart, p.startInstant(), p.endInstant());
        if (retentionRate != null) {
            retentionRate = retentionRate.setScale(4, RoundingMode.HALF_UP);
        }

        List<OrderRepository.TopSpenderProjection> topSpendersRaw =
                orderRepository.findTopSpendersByCompletedOrderTotal(
                        p.startInstant(), p.endInstant(), PageRequest.of(0, TOP_N));
        List<CustomersReportResponse.TopSpenderItem> topSpenders = topSpendersRaw.stream()
                .map(t -> new CustomersReportResponse.TopSpenderItem(
                        t.getCustomerId(), t.getFullName(), t.getCompletedOrders(), t.getTotalSpent()))
                .toList();

        BigDecimal totalSpend = orderRepository.sumCompletedTotalQuoteBetween(p.startInstant(), p.endInstant());
        BigDecimal avgSpendPerPayingCustomer = (mau > 0)
                ? totalSpend.divide(BigDecimal.valueOf(mau), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long newCustomersInPeriod = userRepository.countNewCustomersBetween(
                toInstant(p.startInstant()), toInstant(p.endInstant()));

        CustomersReportResponse.Compare compare = null;
        if ("PREVIOUS_PERIOD".equals(actualCompare)) {
            long periodDays = ChronoUnit.DAYS.between(p.startDate(), p.endDate());
            LocalDate prevEnd = p.startDate();
            LocalDate prevStart = prevEnd.minusDays(periodDays);
            OffsetDateTime prevStartOdt = prevStart.atStartOfDay(VN_ZONE).toOffsetDateTime();
            OffsetDateTime prevEndOdt = prevEnd.atStartOfDay(VN_ZONE).toOffsetDateTime();

            long prevNew = userRepository.countNewCustomersBetween(
                    prevStartOdt.toInstant(), prevEndOdt.toInstant());
            OffsetDateTime prevCohortStart = prevStartOdt.minusDays(30);
            BigDecimal prevRetention = loginEventRepository.calculateCustomerRetentionRate30d(
                    prevCohortStart, prevStartOdt, prevEndOdt);

            compare = new CustomersReportResponse.Compare(
                    percentChange(BigDecimal.valueOf(prevNew), BigDecimal.valueOf(newCustomersInPeriod)),
                    percentChange(
                            prevRetention != null ? prevRetention : BigDecimal.ZERO,
                            retentionRate != null ? retentionRate : BigDecimal.ZERO));
        }

        List<FinancialReportResponse.DataQualityWarning> dataQuality = List.of();

        return new CustomersReportResponse(
                new FinancialReportResponse.Period(p.startDate(), p.endDate()),
                totalCustomers,
                activeUsers,
                retentionRate,
                topSpenders,
                avgSpendPerPayingCustomer,
                newCustomersInPeriod,
                compare,
                dataQuality);
    }

    public PeakHoursReportResponse peakHoursReport(LocalDate periodStart, LocalDate periodEnd) {
        ResolvedPeriod p = resolvePeriod(periodStart, periodEnd);

        List<OrderRepository.PeakHourProjection> rawCells =
                orderRepository.countOrdersByScheduledWeekdayHour(p.startInstant(), p.endInstant());

        Map<String, OrderRepository.PeakHourProjection> map = new HashMap<>();
        for (OrderRepository.PeakHourProjection cell : rawCells) {
            map.put(cell.getWeekday() + "-" + cell.getHour(), cell);
        }

        List<PeakHoursReportResponse.HeatmapCell> heatmap = new ArrayList<>(168);
        Integer topWeekday = null;
        Integer topHour = null;
        Long topCount = 0L;
        for (int w = 1; w <= 7; w++) {
            for (int h = 0; h <= 23; h++) {
                OrderRepository.PeakHourProjection cell = map.get(w + "-" + h);
                long orderCount = (cell != null && cell.getOrderCount() != null) ? cell.getOrderCount() : 0L;
                long completedCount = (cell != null && cell.getCompletedCount() != null)
                        ? cell.getCompletedCount()
                        : 0L;
                heatmap.add(new PeakHoursReportResponse.HeatmapCell(w, h, orderCount, completedCount));
                if (orderCount > topCount) {
                    topCount = orderCount;
                    topWeekday = w;
                    topHour = h;
                }
            }
        }

        PeakHoursReportResponse.PeakHourInsight insight = (topCount > 0)
                ? new PeakHoursReportResponse.PeakHourInsight(topWeekday, topHour, topCount)
                : new PeakHoursReportResponse.PeakHourInsight(null, null, 0L);

        long excluded = orderRepository.countMissingScheduleBetween(p.startInstant(), p.endInstant());
        List<FinancialReportResponse.DataQualityWarning> dataQuality = List.of();

        return new PeakHoursReportResponse(
                new FinancialReportResponse.Period(p.startDate(), p.endDate()),
                heatmap,
                insight,
                excluded,
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
