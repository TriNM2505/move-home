package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import vn.movehome.backend.dto.admin.detail.CustomerDetailResponse;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.dto.admin.detail.DriverOrderItem;
import vn.movehome.backend.dto.admin.list.OrderListItem;

@Repository("customerOrderRepository")
public interface OrderRepository extends JpaRepository<ServiceOrder, UUID> {

        interface DistrictCount {
                String getDistrict();

                Long getCount();
        }

        interface VehicleRevenueProjection {
                String getVehicleType();

                BigDecimal getTotalRevenue();
        }

        interface StatusCountProjection {
                String getStatus();

                Long getCount();
        }

        interface OrderTrendProjection {
                String getBucket();

                Long getCreatedCount();

                Long getCompletedCount();
        }

        interface PeakHourProjection {
                Integer getWeekday();

                Integer getHour();

                Long getOrderCount();

                Long getCompletedCount();
        }

        interface TopSpenderProjection {
                UUID getCustomerId();

                String getFullName();

                Long getCompletedOrders();

                BigDecimal getTotalSpent();
        }

        Optional<ServiceOrder> findByIdAndCustomerId(UUID id, UUID customerId);

        Optional<ServiceOrder> findByIdAndCustomerIdAndDeletedAtIsNull(UUID id, UUID customerId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from CustomerServiceOrder o where o.id = :id")
        Optional<ServiceOrder> findByIdForUpdate(@Param("id") UUID id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT o
                        FROM CustomerServiceOrder o
                        WHERE o.id = :id
                          AND o.customerId = :customerId
                          AND o.deletedAt IS NULL
                        """)
        Optional<ServiceOrder> findByIdAndCustomerIdForUpdate(
                        @Param("id") UUID id,
                        @Param("customerId") UUID customerId);

        Page<ServiceOrder> findByCustomerIdAndDeletedAtIsNull(UUID customerId, Pageable pageable);

        long countByCustomerIdAndDeletedAtIsNull(UUID customerId);

        Page<ServiceOrder> findByCustomerIdAndStatusAndDeletedAtIsNull(
                        UUID customerId,
                        String status,
                        Pageable pageable);

        long countByCustomerIdAndStatusAndDeletedAtIsNull(UUID customerId, String status);

        Page<ServiceOrder> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

        // Don dang giao (dung cho service mo phong di chuyen tai xe)
        List<ServiceOrder> findByStatusInAndDeletedAtIsNull(Collection<String> statuses);

        // Escrow: don da COMPLETED, chua release thu nhap, hoan thanh truoc moc cutoff (het 2h).
        List<ServiceOrder> findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                        String status, OffsetDateTime cutoff);

        // Tai xe co dang ban 1 don nao khong (chan nhan >1 don cung luc)
        boolean existsByDriverIdAndStatusInAndDeletedAtIsNull(UUID driverId, Collection<String> statuses);

        Page<ServiceOrder> findByDriverIdAndDeletedAtIsNull(UUID driverId, Pageable pageable);

        Page<ServiceOrder> findByDriverIdAndStatusAndDeletedAtIsNull(
                        UUID driverId,
                        String status,
                        Pageable pageable);

        long countByDriverIdAndStatusAndDeletedAtIsNull(UUID driverId, String status);

        Optional<ServiceOrder> findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        UUID driverId,
                        String status);

        Optional<ServiceOrder> findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        UUID driverId,
                        Collection<String> statuses);

        Optional<ServiceOrder> findByIdAndDeletedAtIsNull(UUID id);

        @Query("""
                        select new vn.movehome.backend.dto.admin.list.OrderListItem(
                            so.id,
                            so.orderCode,
                            cust.fullName,
                            drv.fullName,
                            so.vehicleType,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.totalQuote,
                            so.status,
                            so.createdAt,
                            so.scheduledAt
                        )
                        from CustomerServiceOrder so
                        join User cust on cust.id = so.customerId
                        left join User drv on drv.id = so.driverId
                        where so.deletedAt is null
                          and (coalesce(:status, '') = '' or so.status = :status)
                          and (
                              coalesce(:search, '') = ''
                              or lower(so.orderCode) like lower(concat('%', coalesce(:search, ''), '%'))
                              or lower(cust.fullName) like lower(concat('%', coalesce(:search, ''), '%'))
                              or lower(drv.fullName) like lower(concat('%', coalesce(:search, ''), '%'))
                          )
                          and (cast(:from as java.time.OffsetDateTime) is null or so.createdAt >= :from)
                          and (cast(:to as java.time.OffsetDateTime) is null or so.createdAt < :to)
                        """)
        Page<OrderListItem> findAdminOrderList(
                        @Param("status") String status,
                        @Param("search") String search,
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to,
                        Pageable pageable);

        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.DriverDetailResponse$RecentOrderItem(
                            so.id,
                            so.orderCode,
                            so.status,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.totalQuote,
                            so.createdAt
                        )
                        from CustomerServiceOrder so
                        where so.driverId = :driverId
                          and so.deletedAt is null
                        order by so.createdAt desc, so.id desc
                        """)
        List<DriverDetailResponse.RecentOrderItem> findRecentOrdersByDriver(
                        @Param("driverId") UUID driverId,
                        Pageable pageable);

        @Query("""
                        select coalesce(sum(so.totalQuote), 0)
                        from CustomerServiceOrder so
                        where so.customerId = :customerId
                          and so.status = 'COMPLETED'
                          and so.deletedAt is null
                        """)
        BigDecimal sumCompletedTotalQuoteByCustomer(@Param("customerId") UUID customerId);

        @Query("""
                        select min(so.createdAt)
                        from CustomerServiceOrder so
                        where so.customerId = :customerId
                          and so.deletedAt is null
                        """)
        Optional<OffsetDateTime> findFirstOrderAtByCustomer(@Param("customerId") UUID customerId);

        @Query("""
                        select max(so.createdAt)
                        from CustomerServiceOrder so
                        where so.customerId = :customerId
                          and so.deletedAt is null
                        """)
        Optional<OffsetDateTime> findLastOrderAtByCustomer(@Param("customerId") UUID customerId);

        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.CustomerDetailResponse$RecentOrderItem(
                            so.id,
                            so.orderCode,
                            so.status,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.totalQuote,
                            so.createdAt
                        )
                        from CustomerServiceOrder so
                        where so.customerId = :customerId
                          and so.deletedAt is null
                        order by so.createdAt desc, so.id desc
                        """)
        List<CustomerDetailResponse.RecentOrderItem> findRecentOrdersByCustomer(
                        @Param("customerId") UUID customerId,
                        Pageable pageable);

        @Query("""
                        select so.pickupDistrict as district, count(so) as count
                        from CustomerServiceOrder so
                        where so.customerId = :customerId
                          and so.deletedAt is null
                          and so.pickupDistrict is not null
                        group by so.pickupDistrict
                        """)
        List<DistrictCount> countPickupDistrictsByCustomer(@Param("customerId") UUID customerId);

        @Query("""
                        select so.dropoffDistrict as district, count(so) as count
                        from CustomerServiceOrder so
                        where so.customerId = :customerId
                          and so.deletedAt is null
                          and so.dropoffDistrict is not null
                        group by so.dropoffDistrict
                        """)
        List<DistrictCount> countDropoffDistrictsByCustomer(@Param("customerId") UUID customerId);

        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.DriverOrderItem(
                            so.id,
                            so.orderCode,
                            so.status,
                            cust.fullName,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.vehicleType,
                            so.totalQuote,
                            so.createdAt,
                            so.scheduledAt
                        )
                        from CustomerServiceOrder so
                        join User cust on cust.id = so.customerId
                        where so.driverId = :driverId
                          and so.deletedAt is null
                          and (coalesce(:status, '') = '' or so.status = :status)
                        """)
        Page<DriverOrderItem> findDriverOrderHistory(
                        @Param("driverId") UUID driverId,
                        @Param("status") String status,
                        Pageable pageable);

        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.CustomerOrderItem(
                            so.id,
                            so.orderCode,
                            so.status,
                            drv.fullName,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.vehicleType,
                            so.totalQuote,
                            so.createdAt,
                            so.scheduledAt
                        )
                        from CustomerServiceOrder so
                        left join User drv on drv.id = so.driverId
                        where so.customerId = :customerId
                          and so.deletedAt is null
                          and (coalesce(:status, '') = '' or so.status = :status)
                        """)
        Page<CustomerOrderItem> findCustomerOrderHistory(
                        @Param("customerId") UUID customerId,
                        @Param("status") String status,
                        Pageable pageable);

        /**
         * Danh sach don cua chinh Customer, KHONG thuoc cac trang thai truyen vao.
         * Dung cho trang "Don dang cho" (pending = chua ket thuc: khong COMPLETED/CANCELLED).
         * Filter theo customerId dam bao khach chi thay don cua minh (HR-10).
         */
        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.CustomerOrderItem(
                            so.id,
                            so.orderCode,
                            so.status,
                            drv.fullName,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.vehicleType,
                            so.totalQuote,
                            so.createdAt,
                            so.scheduledAt
                        )
                        from CustomerServiceOrder so
                        left join User drv on drv.id = so.driverId
                        where so.customerId = :customerId
                          and so.deletedAt is null
                          and so.status not in :statuses
                        order by so.createdAt desc
                        """)
        Page<CustomerOrderItem> findCustomerOrdersByStatusNotIn(
                        @Param("customerId") UUID customerId,
                        @Param("statuses") Collection<String> statuses,
                        Pageable pageable);

        /**
         * Danh sach don cua chinh Customer thuoc cac trang thai truyen vao.
         * Dung cho trang "Lich su" (history = COMPLETED, CANCELLED).
         * Filter theo customerId dam bao khach chi thay don cua minh (HR-10).
         */
        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.CustomerOrderItem(
                            so.id,
                            so.orderCode,
                            so.status,
                            drv.fullName,
                            so.pickupDistrict,
                            so.dropoffDistrict,
                            so.vehicleType,
                            so.totalQuote,
                            so.createdAt,
                            so.scheduledAt
                        )
                        from CustomerServiceOrder so
                        left join User drv on drv.id = so.driverId
                        where so.customerId = :customerId
                          and so.deletedAt is null
                          and so.status in :statuses
                        order by so.createdAt desc
                        """)
        Page<CustomerOrderItem> findCustomerOrdersByStatusIn(
                        @Param("customerId") UUID customerId,
                        @Param("statuses") Collection<String> statuses,
                        Pageable pageable);

        @Query("""
                        select coalesce(sum(so.totalQuote), 0)
                        from CustomerServiceOrder so
                        where so.status = 'COMPLETED'
                          and so.completedAt >= :from and so.completedAt < :to
                          and so.deletedAt is null
                        """)
        BigDecimal sumCompletedTotalQuoteBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select so.vehicleType as vehicleType, coalesce(sum(so.totalQuote), 0) as totalRevenue
                        from CustomerServiceOrder so
                        where so.status = 'COMPLETED'
                          and so.completedAt >= :from and so.completedAt < :to
                          and so.deletedAt is null
                        group by so.vehicleType
                        """)
        List<VehicleRevenueProjection> sumCompletedTotalQuoteByVehicleBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select coalesce(avg(so.totalQuote), 0)
                        from CustomerServiceOrder so
                        where so.status = 'COMPLETED'
                          and so.completedAt >= :from and so.completedAt < :to
                          and so.deletedAt is null
                        """)
        BigDecimal averageCompletedOrderValueBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select coalesce(avg(so.distanceKm), 0)
                        from CustomerServiceOrder so
                        where so.createdAt >= :from and so.createdAt < :to
                          and so.deletedAt is null
                        """)
        BigDecimal averageDistanceKmForCreatedOrdersBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select count(so)
                        from CustomerServiceOrder so
                        where so.createdAt >= :from and so.createdAt < :to
                          and so.deletedAt is null
                        """)
        long countCreatedOrdersBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select count(so)
                        from CustomerServiceOrder so
                        where so.createdAt >= :from and so.createdAt < :to
                          and so.status = :status
                          and so.deletedAt is null
                        """)
        long countCreatedOrdersByStatusBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to,
                        @Param("status") String status);

        @Query("""
                        select so.status as status, count(so) as count
                        from CustomerServiceOrder so
                        where so.createdAt >= :from and so.createdAt < :to
                          and so.deletedAt is null
                        group by so.status
                        """)
        List<StatusCountProjection> countCreatedOrdersGroupByStatusBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select count(so)
                        from CustomerServiceOrder so
                        where so.createdAt >= :from and so.createdAt < :to
                          and so.peakSurcharge is not null and so.peakSurcharge > 0
                          and so.deletedAt is null
                        """)
        long countPeakSurchargeOrdersBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query(value = """
                        SELECT
                            TO_CHAR(d.bucket AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS "bucket",
                            COUNT(*) FILTER (WHERE d.event = 'CREATED') AS "createdCount",
                            COUNT(*) FILTER (WHERE d.event = 'COMPLETED') AS "completedCount"
                        FROM (
                            SELECT created_at AS bucket, 'CREATED' AS event
                            FROM service_order
                            WHERE created_at >= :from AND created_at < :to AND deleted_at IS NULL
                            UNION ALL
                            SELECT completed_at AS bucket, 'COMPLETED' AS event
                            FROM service_order
                            WHERE completed_at >= :from AND completed_at < :to AND deleted_at IS NULL
                              AND status = 'COMPLETED'
                        ) d
                        GROUP BY TO_CHAR(d.bucket AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD')
                        ORDER BY "bucket"
                        """, nativeQuery = true)
        List<OrderTrendProjection> findCompletionTrend(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query(value = """
                        SELECT
                            EXTRACT(ISODOW FROM scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS "weekday",
                            EXTRACT(HOUR FROM scheduled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::int AS "hour",
                            COUNT(*) AS "orderCount",
                            COUNT(*) FILTER (WHERE status = 'COMPLETED') AS "completedCount"
                        FROM service_order
                        WHERE scheduled_at IS NOT NULL
                          AND scheduled_at >= :from AND scheduled_at < :to
                          AND deleted_at IS NULL
                        GROUP BY "weekday", "hour"
                        ORDER BY "weekday", "hour"
                        """, nativeQuery = true)
        List<PeakHourProjection> countOrdersByScheduledWeekdayHour(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select count(so)
                        from CustomerServiceOrder so
                        where so.scheduledAt is null
                          and so.createdAt >= :from and so.createdAt < :to
                          and so.deletedAt is null
                        """)
        long countMissingScheduleBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query("""
                        select
                            so.customerId as customerId,
                            cust.fullName as fullName,
                            count(so) as completedOrders,
                            coalesce(sum(so.totalQuote), 0) as totalSpent
                        from CustomerServiceOrder so
                        join User cust on cust.id = so.customerId
                        where so.status = 'COMPLETED'
                          and so.completedAt >= :from and so.completedAt < :to
                          and so.deletedAt is null
                        group by so.customerId, cust.fullName
                        order by coalesce(sum(so.totalQuote), 0) desc, so.customerId asc
                        """)
        List<TopSpenderProjection> findTopSpendersByCompletedOrderTotal(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to,
                        Pageable pageable);

        @Query("""
                        select count(distinct u.id)
                        from User u
                        where u.role = vn.movehome.backend.entity.UserRole.DRIVER
                          and u.deletedAt is null
                          and u.id not in (
                              select distinct so.driverId
                              from CustomerServiceOrder so
                              where so.driverId is not null
                                and so.createdAt >= :churnFrom and so.createdAt < :periodEnd
                                and so.deletedAt is null
                          )
                        """)
        long countChurnedDrivers(
                        @Param("churnFrom") OffsetDateTime churnFrom,
                        @Param("periodEnd") OffsetDateTime periodEnd);

        @Query("""
                        select count(distinct so.customerId)
                        from CustomerServiceOrder so
                        where so.createdAt >= :from and so.createdAt < :to
                          and so.deletedAt is null
                        """)
        long countMauBetween(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query(value = """
                        SELECT COALESCE(AVG(daily_count), 0)
                        FROM (
                            SELECT COUNT(DISTINCT customer_id) AS daily_count
                            FROM service_order
                            WHERE created_at >= :from AND created_at < :to
                              AND deleted_at IS NULL
                            GROUP BY DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
                        ) sub
                        """, nativeQuery = true)
        BigDecimal calculateDauAverage(
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

        @Query(value = """
                        SELECT
                            COUNT(DISTINCT u.id) FILTER (
                                WHERE EXISTS (
                                    SELECT 1 FROM service_order so2
                                    WHERE so2.customer_id = u.id
                                      AND so2.created_at >= :periodStart AND so2.created_at < :periodEnd
                                      AND so2.deleted_at IS NULL
                                )
                            )::decimal / NULLIF(COUNT(DISTINCT u.id), 0) AS rate
                        FROM app_user u
                        WHERE u.role = 'CUSTOMER'
                          AND u.deleted_at IS NULL
                          AND u.created_at >= :cohortStart AND u.created_at < :periodStart
                        """, nativeQuery = true)
        BigDecimal calculateRetentionRate30d(
                        @Param("cohortStart") OffsetDateTime cohortStart,
                        @Param("periodStart") OffsetDateTime periodStart,
                        @Param("periodEnd") OffsetDateTime periodEnd);

        boolean existsByOrderCode(String orderCode);
}
