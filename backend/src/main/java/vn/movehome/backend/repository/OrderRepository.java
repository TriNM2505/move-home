package vn.movehome.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.Order;
import vn.movehome.backend.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho bang service_order.
 * @SQLRestriction("deleted_at IS NULL") duoc apply tu dong tu Order entity.
 * Native SQL query (nativeQuery=true) dung cho cac aggregation phuc tap cua Dashboard.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Tim don hang theo ma don (vd: "MH202606010001").
     * Dung khi Driver hoac Customer xem chi tiet don bang ma don.
     */
    Optional<Order> findByOrderCode(String orderCode);

    /**
     * 10 don hang moi nhat (bat ky status) — cho Admin Dashboard FR-014.
     * Spring Data tu them ORDER BY created_at DESC + LIMIT 10.
     */
    List<Order> findTop10ByOrderByCreatedAtDesc();

    /**
     * Dem so don theo trang thai — cho Admin Dashboard KPI (FR-007, FR-009).
     * Vi du: countByStatus(OrderStatus.DISPUTED) = so don dang tranh chap.
     */
    long countByStatus(OrderStatus status);

    /**
     * Tinh tong doanh thu commission tu cac don COMPLETED trong khoang thoi gian.
     * Cong thuc: commission = total_quote * commission_rate_snapshot (Spec #028 FR-006).
     * Dung native SQL vi JPQL khong ho tro nhan 2 column truc tiep.
     * Tra ve null neu khong co don nao — caller phai dung COALESCE hoac xu ly null.
     *
     * @param since  moc thoi gian bat dau (UTC Instant)
     */
    @Query(
        value = """
            SELECT COALESCE(
                SUM(total_quote * commission_rate_snapshot), 0
            )
            FROM service_order
            WHERE status = 'COMPLETED'
              AND completed_at >= :since
              AND deleted_at IS NULL
            """,
        nativeQuery = true
    )
    BigDecimal sumRevenueSince(@Param("since") Instant since);

    /**
     * Doanh thu commission theo ngay, dung de ve Bar Chart (Spec #028 FR-010).
     * Tra ve mang Object[] moi phan tu la [date_string (yyyy-MM-dd), revenue (BigDecimal)].
     * Caller phai tao date series day du (bao gom ngay khong co don — FR-012).
     *
     * @param since moc thoi gian bat dau (30 ngay truoc hom nay)
     */
    @Query(
        value = """
            SELECT
                TO_CHAR(completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day,
                COALESCE(SUM(total_quote * commission_rate_snapshot), 0)             AS revenue
            FROM service_order
            WHERE status = 'COMPLETED'
              AND completed_at >= :since
              AND deleted_at IS NULL
            GROUP BY day
            ORDER BY day
            """,
        nativeQuery = true
    )
    List<Object[]> findRevenueByDaySince(@Param("since") Instant since);

    /**
     * So don theo ngay, dung de ve Bar Chart so don (Spec #028 FR-010).
     * Tra ve mang Object[] moi phan tu la [date_string (yyyy-MM-dd), count (Long)].
     * Tinh theo gio Asia/Ho_Chi_Minh (AC-07 — convert truoc khi group).
     *
     * @param since moc thoi gian bat dau
     */
    @Query(
        value = """
            SELECT
                TO_CHAR(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day,
                COUNT(*)                                                            AS order_count
            FROM service_order
            WHERE created_at >= :since
              AND deleted_at IS NULL
            GROUP BY day
            ORDER BY day
            """,
        nativeQuery = true
    )
    List<Object[]> findOrderCountByDaySince(@Param("since") Instant since);

    /**
     * Doanh thu commission theo thang, dung de ve Line Chart 12 thang (Spec #028 FR-011).
     * Tra ve mang Object[] moi phan tu la [month_string (yyyy-MM), revenue (BigDecimal)].
     *
     * @param since moc thoi gian bat dau (12 thang truoc)
     */
    @Query(
        value = """
            SELECT
                TO_CHAR(completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM') AS month,
                COALESCE(SUM(total_quote * commission_rate_snapshot), 0)           AS revenue
            FROM service_order
            WHERE status = 'COMPLETED'
              AND completed_at >= :since
              AND deleted_at IS NULL
            GROUP BY month
            ORDER BY month
            """,
        nativeQuery = true
    )
    List<Object[]> findRevenueByMonthSince(@Param("since") Instant since);

    /**
     * 10 don hang moi nhat co Pageable — linh hoat hon findTop10.
     * Dung khi can kiem soat LIMIT tu caller (Phase 3B endpoint FR-014).
     */
    @Query("FROM ServiceOrder o ORDER BY o.createdAt DESC")
    List<Order> findRecentOrders(Pageable pageable);

    // ===== Them cho Phase 3B Admin Dashboard =====

    /**
     * Dem don hang co created_at >= moc thoi gian (de tinh "hom nay" va "thang nay").
     * @SQLRestriction tu dong loc deleted_at IS NULL.
     */
    long countByCreatedAtGreaterThanEqual(Instant since);

    /**
     * Tong total_quote cua COMPLETED orders trong khoang thoi gian.
     * Khac sumRevenueSince: day la tong doanh thu truoc khi tru commission.
     */
    @Query(
        value = """
            SELECT COALESCE(SUM(total_quote), 0)
            FROM service_order
            WHERE status = 'COMPLETED'
              AND completed_at >= :since
              AND deleted_at IS NULL
            """,
        nativeQuery = true
    )
    BigDecimal sumTotalQuoteSince(@Param("since") Instant since);

    /**
     * Thong ke theo ngay: so don created + doanh thu COMPLETED tren cung 1 ngay.
     * Tra Object[] moi phan tu: [day (String yyyy-MM-dd), order_count (Long),
     *                            revenue (BigDecimal), commission (BigDecimal)]
     * Dung cho RevenueByDayResponse trong service (gop tu 2 nguon: created vs completed).
     */
    @Query(
        value = """
            SELECT
                TO_CHAR(completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day,
                COUNT(*)                                                              AS order_count,
                COALESCE(SUM(total_quote), 0)                                        AS revenue,
                COALESCE(SUM(total_quote * commission_rate_snapshot), 0)             AS commission
            FROM service_order
            WHERE status = 'COMPLETED'
              AND completed_at >= :since
              AND deleted_at IS NULL
            GROUP BY day
            ORDER BY day
            """,
        nativeQuery = true
    )
    List<Object[]> findDailyCompletedStats(@Param("since") Instant since);

    /**
     * N don hang gan nhat kem ten Customer va Driver (LEFT JOIN — Driver co the NULL).
     * Tra Object[]: [order_id UUID, order_code String, status String,
     *               total_quote BigDecimal, created_at Timestamp,
     *               customer_name String, driver_name String|null]
     */
    @Query(
        value = """
            SELECT
                o.id            AS order_id,
                o.order_code,
                o.status,
                o.total_quote,
                o.created_at,
                c.full_name     AS customer_name,
                d.full_name     AS driver_name
            FROM service_order o
            JOIN  app_user c ON c.id = o.customer_id
            LEFT JOIN app_user d ON d.id = o.driver_id
            WHERE o.deleted_at IS NULL
            ORDER BY o.created_at DESC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<Object[]> findRecentOrdersWithNames(@Param("limit") int limit);

    // ===== Phase 3C: Admin Order List =====

    /**
     * Tat ca don hang cho trang Admin orders.html — kem ten Customer va Driver (Phase 3C).
     * Tra Object[]: [id, order_code, customer_name, driver_name, status, pickup_district,
     *               dropoff_district, total_quote, commission_rate_snapshot, scheduled_at,
     *               created_at, completed_at]
     * driver_name co the null neu don chua duoc phan cong.
     */
    @Query(
        value = """
            SELECT o.id, o.order_code,
                   c.full_name AS customer_name, d.full_name AS driver_name,
                   o.status, o.pickup_district, o.dropoff_district,
                   o.total_quote, o.commission_rate_snapshot,
                   o.scheduled_at, o.created_at, o.completed_at
            FROM service_order o
            JOIN app_user c ON c.id = o.customer_id
            LEFT JOIN app_user d ON d.id = o.driver_id
            WHERE o.deleted_at IS NULL
            ORDER BY o.created_at DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAllOrdersForAdmin();

    /**
     * Don hang cho Admin, loc theo status cu the (Phase 3C).
     * @param status gia tri enum name trong OrderStatus runtime.
     */
    @Query(
        value = """
            SELECT o.id, o.order_code,
                   c.full_name AS customer_name, d.full_name AS driver_name,
                   o.status, o.pickup_district, o.dropoff_district,
                   o.total_quote, o.commission_rate_snapshot,
                   o.scheduled_at, o.created_at, o.completed_at
            FROM service_order o
            JOIN app_user c ON c.id = o.customer_id
            LEFT JOIN app_user d ON d.id = o.driver_id
            WHERE o.deleted_at IS NULL AND o.status = :status
            ORDER BY o.created_at DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAllOrdersByStatusForAdmin(@Param("status") String status);
}
