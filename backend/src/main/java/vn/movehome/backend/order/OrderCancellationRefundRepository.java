package vn.movehome.backend.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderCancellationRefundRepository extends JpaRepository<OrderCancellationRefund, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<OrderCancellationRefund> findByOrderIdAndCustomerId(UUID orderId, UUID customerId);

    Page<OrderCancellationRefund> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Page<OrderCancellationRefund> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<OrderCancellationRefund> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Khoa bi ghi khi Manager duyet — chong double-process (hoan/tu choi 2 lan).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrderCancellationRefund c where c.id = :id")
    Optional<OrderCancellationRefund> findByIdForUpdate(@Param("id") UUID id);
}
