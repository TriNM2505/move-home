package vn.movehome.backend.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("customerOrderRepository")
public interface OrderRepository extends JpaRepository<ServiceOrder, UUID> {

    Optional<ServiceOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<ServiceOrder> findByCustomerIdAndDeletedAtIsNull(UUID customerId, Pageable pageable);

    Page<ServiceOrder> findByCustomerIdAndStatusAndDeletedAtIsNull(
            UUID customerId,
            String status,
            Pageable pageable);

    boolean existsByOrderCode(String orderCode);
}
