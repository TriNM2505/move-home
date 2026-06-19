package vn.movehome.backend.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository("customerOrderRepository")
public interface OrderRepository extends JpaRepository<ServiceOrder, UUID> {

    Optional<ServiceOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<ServiceOrder> findByIdAndCustomerIdAndDeletedAtIsNull(UUID id, UUID customerId);

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

    boolean existsByOrderCode(String orderCode);
}
