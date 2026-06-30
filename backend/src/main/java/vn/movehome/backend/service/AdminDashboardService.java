package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.dto.admin.*;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.OrderRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service cho Admin Dashboard — cac query read-only tong hop du lieu.
 * @Transactional(readOnly=true): toi uu cho cac query SELECT thuan tuy.
 * Constitution AC-07: moi tinh toan "hom nay" / "thang nay" phai dung Asia/Ho_Chi_Minh.
 * Constitution AC-08: money fields phai la BigDecimal scale=0.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int CHART_DAYS  = 30;
    private static final int TOP_DRIVERS = 5;
    private static final int RECENT_ORDERS_LIMIT = 10;

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final OrderRepository orderRepository;

    // ===== KPI =====

    /**
     * 10 KPI cho hang dau trang (Spec #028 FR-004..FR-009).
     * Tinh "hom nay" va "thang nay" theo Asia/Ho_Chi_Minh (AC-07).
     */
    public KpiResponse getKpi() {
        log.info("[Dashboard] Dang tinh KPI...");

        // Moc thoi gian theo gio Viet Nam (AC-07)
        LocalDate today = LocalDate.now(VN_ZONE);
        Instant todayStart  = today.atStartOfDay(VN_ZONE).toInstant();
        Instant monthStart  = YearMonth.now(VN_ZONE).atDay(1).atStartOfDay(VN_ZONE).toInstant();

        long totalCustomers      = userRepository.countByRoleAndDeletedAtIsNull(UserRole.CUSTOMER);
        long activeDrivers       = userRepository.countByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.ACTIVE);
        long pendingApprovals    = userRepository.countByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.PENDING_APPROVAL);
        long ordersToday         = orderRepository.countByCreatedAtGreaterThanEqual(todayStart);
        long ordersThisMonth     = orderRepository.countByCreatedAtGreaterThanEqual(monthStart);
        BigDecimal revenueMonth  = coalesce(orderRepository.sumTotalQuoteSince(monthStart));
        BigDecimal commMonth     = coalesce(orderRepository.sumRevenueSince(monthStart));
        long pendingOrders       = countStatuses(OrderStatus.PENDING, OrderStatus.PENDING_PAYMENT);
        long completedOrders     = orderRepository.countByStatus(OrderStatus.COMPLETED);
        long inDisputeOrders     = countStatuses(OrderStatus.DISPUTED, OrderStatus.IN_DISPUTE);

        log.debug("[Dashboard] KPI: customers={}, activeDrivers={}, pendingApprovals={}, ordersToday={}, revenueMonth={}",
                totalCustomers, activeDrivers, pendingApprovals, ordersToday, revenueMonth);

        return new KpiResponse(totalCustomers, activeDrivers, pendingApprovals,
                ordersToday, ordersThisMonth, revenueMonth, commMonth,
                pendingOrders, completedOrders, inDisputeOrders);
    }

    // ===== REVENUE CHART =====

    /**
     * Du lieu bieu do doanh thu 30 ngay (Spec #028 FR-010, FR-012).
     * Luon tra du 30 phan tu — ngay khong co don = 0 (FR-012).
     * revenue/commission tinh tu COMPLETED orders theo completed_at.
     */
    public RevenueByDayResponse getRevenueByDay() {
        log.info("[Dashboard] Dang lay du lieu bieu do 30 ngay...");

        LocalDate today = LocalDate.now(VN_ZONE);
        Instant since = today.minusDays(CHART_DAYS - 1).atStartOfDay(VN_ZONE).toInstant();

        // Lay du lieu tu DB: [day, count, revenue, commission]
        List<Object[]> dbRows = orderRepository.findDailyCompletedStats(since);

        // Build map: "yyyy-MM-dd" → row
        Map<String, Object[]> byDate = dbRows.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));

        // Tao series 30 ngay day du — ngay khong co du lieu dien 0 (FR-012)
        List<RevenueByDayResponse.RevenuePoint> points = new ArrayList<>(CHART_DAYS);
        for (int i = CHART_DAYS - 1; i >= 0; i--) {
            String dateStr = today.minusDays(i).toString();  // "yyyy-MM-dd"
            Object[] row = byDate.get(dateStr);

            long count       = row != null ? toLong(row[1])       : 0L;
            BigDecimal rev   = row != null ? toBigDecimal(row[2]) : BigDecimal.ZERO;
            BigDecimal comm  = row != null ? toBigDecimal(row[3]) : BigDecimal.ZERO;

            points.add(new RevenueByDayResponse.RevenuePoint(dateStr, rev, comm, count));
        }

        return new RevenueByDayResponse(points);
    }

    // ===== TOP DRIVERS =====

    /**
     * Top 5 Driver co tong doanh thu cao nhat.
     * Goi N+1 query (N=5) de lay ten Driver — chap nhan duoc vi N nho.
     */
    public DriverPerformanceResponse getTopDrivers() {
        log.info("[Dashboard] Dang lay top {} drivers...", TOP_DRIVERS);

        var topProfiles = driverProfileRepository.findTop5ByOrderByTotalRevenueDesc();

        var stats = topProfiles.stream()
                .map(p -> {
                    String name = userRepository.findById(p.getUserId())
                            .map(u -> u.getFullName())
                            .orElse("Driver khong xac dinh");
                    return new DriverPerformanceResponse.DriverStat(
                            p.getUserId(),
                            name,
                            p.getTotalOrdersCompleted().longValue(),
                            p.getTotalRevenue(),
                            p.getAverageRating()
                    );
                })
                .collect(Collectors.toList());

        return new DriverPerformanceResponse(stats);
    }

    // ===== RECENT ORDERS =====

    /**
     * 10 don hang gan nhat kem ten Customer va Driver (Spec #028 FR-014, FR-015).
     * Driver = null cho don chua phan cong (FR-015: FE hien thi em-dash).
     */
    public RecentOrderResponse getRecentOrders() {
        log.info("[Dashboard] Dang lay {} don hang gan nhat...", RECENT_ORDERS_LIMIT);

        List<Object[]> rows = orderRepository.findRecentOrdersWithNames(RECENT_ORDERS_LIMIT);

        var items = rows.stream()
                .map(r -> new RecentOrderResponse.RecentOrderItem(
                        toUUID(r[0]),                         // order_id
                        (String) r[1],                        // order_code
                        (String) r[5],                        // customer_name
                        (String) r[6],                        // driver_name (nullable)
                        OrderStatus.valueOf((String) r[2]),   // status
                        toBigDecimal(r[3]),                   // total_quote
                        toInstant(r[4])                       // created_at
                ))
                .collect(Collectors.toList());

        return new RecentOrderResponse(items);
    }

    // ===== STATUS DISTRIBUTION =====

    /**
     * Phan bo don hang theo tung trang thai runtime (10 status, tru AWAITING_FINAL_PAYMENT).
     * Moi status deu xuat hien du count = 0.
     */
    public OrderStatusDistribution getStatusDistribution() {
        Map<OrderStatus, Long> dist = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            dist.put(s, orderRepository.countByStatus(s));
        }
        return new OrderStatusDistribution(dist);
    }

    // ===== OVERVIEW (combined) =====

    /**
     * Tra ve tat ca du lieu dashboard trong 1 request (Spec #028 FR-016).
     * Frontend goi endpoint nay thay vi 5 endpoint rieng le.
     */
    public DashboardOverviewResponse getOverview() {
        log.info("[Dashboard] Dang tai toan bo overview...");
        return new DashboardOverviewResponse(
                getKpi(),
                getRevenueByDay(),
                getTopDrivers(),
                getRecentOrders(),
                getStatusDistribution()
        );
    }

    // ===== ORDER LIST (Phase 3C) =====

    /**
     * Danh sach don hang cho trang Admin orders.html (Phase 3C).
     * Optional filter theo status. Sort by created_at DESC (moi nhat truoc).
     * Tra Page<OrderListItem> de controller co the phan trang theo query param.
     * @param filterStatus null = lay tat ca status
     */
    public Page<OrderListItem> getAllOrders(OrderStatus filterStatus, Pageable pageable) {
        log.info("[Dashboard] Loading orders list, filter={}", filterStatus);

        List<Object[]> rows = filterStatus != null
                ? orderRepository.findAllOrdersByStatusForAdmin(filterStatus.name())
                : orderRepository.findAllOrdersForAdmin();

        List<OrderListItem> items = rows.stream()
                .map(this::mapToOrderListItem)
                .collect(Collectors.toList());

        // Manual paging — data nho (~30 orders cho demo), fetch all roi cat
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), items.size());
        List<OrderListItem> paged = start < items.size() ? items.subList(start, end) : List.of();

        return new PageImpl<>(paged, pageable, items.size());
    }

    private OrderListItem mapToOrderListItem(Object[] r) {
        // Thu tu column theo query: id[0], order_code[1], customer_name[2], driver_name[3],
        //   status[4], pickup_district[5], dropoff_district[6], total_quote[7],
        //   commission_rate_snapshot[8], scheduled_at[9], created_at[10], completed_at[11]
        return new OrderListItem(
                toUUID(r[0]),
                (String) r[1],
                (String) r[2],
                (String) r[3],                                       // driver_name nullable
                OrderStatus.valueOf((String) r[4]),
                (String) r[5],                                       // pickup_district nullable
                (String) r[6],                                       // dropoff_district nullable
                toBigDecimal(r[7]),
                toBigDecimal(r[8]),
                toInstant(r[9]),
                toInstant(r[10]),
                r[11] != null ? toInstant(r[11]) : null              // completed_at nullable
        );
    }

    // ===== DRIVER LIST (Phase 3C) =====

    /**
     * Danh sach tai xe cho trang Admin drivers.html (Phase 3C).
     * LEFT JOIN voi driver_profile — Driver chua submit ho so se co cac field profile = null.
     * @param filterStatus null = lay tat ca status
     */
    public List<DriverListItem> getAllDrivers(UserStatus filterStatus) {
        log.info("[Dashboard] Loading drivers list, filter={}", filterStatus);

        List<Object[]> rows = filterStatus != null
                ? userRepository.findAllDriversByStatusForAdmin(filterStatus.name())
                : userRepository.findAllDriversForAdmin();

        return rows.stream()
                .map(this::mapToDriverListItem)
                .collect(Collectors.toList());
    }

    private DriverListItem mapToDriverListItem(Object[] r) {
        // Thu tu column: user_id[0], full_name[1], email[2], phone[3], status[4],
        //   license_number[5], vehicle_plate[6], vehicle_type[7], deposit_amount[8],
        //   total_orders_completed[9], total_revenue[10], average_rating[11],
        //   created_at[12], approved_at[13]
        return new DriverListItem(
                toUUID(r[0]),
                (String) r[1],
                (String) r[2],
                (String) r[3],                                                  // phone nullable
                UserStatus.valueOf((String) r[4]),
                (String) r[5],                                                  // license_number nullable
                (String) r[6],                                                  // vehicle_plate nullable
                (String) r[7],                                                  // vehicle_type nullable
                r[8]  != null ? toBigDecimal(r[8])              : BigDecimal.ZERO,
                r[9]  != null ? ((Number) r[9]).intValue()       : 0,
                r[10] != null ? toBigDecimal(r[10])              : BigDecimal.ZERO,
                r[11] != null ? toBigDecimal(r[11])              : null,        // average_rating nullable
                toInstant(r[12]),
                r[13] != null ? toInstant(r[13]) : null                        // approved_at nullable
        );
    }

    // ===== CUSTOMER LIST (Phase 3C) =====

    /**
     * Danh sach khach hang cho trang Admin customers.html (Phase 3C).
     * Kem tong so don da dat (ke ca CANCELLED).
     * @param filterStatus null = lay tat ca status
     */
    public List<CustomerListItem> getAllCustomers(UserStatus filterStatus) {
        log.info("[Dashboard] Loading customers list, filter={}", filterStatus);

        List<Object[]> rows = filterStatus != null
                ? userRepository.findAllCustomersByStatusForAdmin(filterStatus.name())
                : userRepository.findAllCustomersForAdmin();

        return rows.stream()
                .map(this::mapToCustomerListItem)
                .collect(Collectors.toList());
    }

    private CustomerListItem mapToCustomerListItem(Object[] r) {
        // Thu tu column: user_id[0], full_name[1], email[2], phone[3], status[4],
        //   email_verified[5], total_orders_placed[6], created_at[7]
        return new CustomerListItem(
                toUUID(r[0]),
                (String) r[1],
                (String) r[2],
                (String) r[3],                                  // phone nullable
                UserStatus.valueOf((String) r[4]),
                toBoolean(r[5]),
                toLong(r[6]),
                toInstant(r[7]),
                null                                            // last_login_at: chua implement tracking
        );
    }

    // ===== PRIVATE HELPERS (type conversion an toan tu Object[]) =====

    private BigDecimal coalesce(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private UUID toUUID(Object val) {
        if (val instanceof UUID uuid) return uuid;
        return UUID.fromString(val.toString());
    }

    private Instant toInstant(Object val) {
        if (val instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (val instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(val.toString());
    }

    private boolean toBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }

    private long countStatuses(OrderStatus... statuses) {
        long count = 0L;
        for (OrderStatus status : statuses) {
            count += orderRepository.countByStatus(status);
        }
        return count;
    }
}
