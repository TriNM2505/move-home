package vn.movehome.backend.order;

import java.time.OffsetDateTime;
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
import vn.movehome.backend.dto.admin.list.OrderListItem;

@Repository("customerOrderRepository")
public interface OrderRepository extends JpaRepository<ServiceOrder, UUID> {

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

        Page<ServiceOrder> findByCustomerIdAndStatusAndDeletedAtIsNull(
                        UUID customerId,
                        String status,
                        Pageable pageable);

        Page<ServiceOrder> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

        Page<ServiceOrder> findByDriverIdAndDeletedAtIsNull(UUID driverId, Pageable pageable);

        Page<ServiceOrder> findByDriverIdAndStatusAndDeletedAtIsNull(
                        UUID driverId,
                        String status,
                        Pageable pageable);

        Optional<ServiceOrder> findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        UUID driverId,
                        String status);

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

        boolean existsByOrderCode(String orderCode);
}
