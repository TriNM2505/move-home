package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderStatusTransitionService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ServiceOrder transition(
            ServiceOrder order,
            String newStatus,
            UUID changedByUserId,
            String changedByRole,
            OffsetDateTime changedAt
    ) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        Objects.requireNonNull(changedByUserId, "changedByUserId must not be null");
        Objects.requireNonNull(changedByRole, "changedByRole must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");

        String oldStatus = order.getStatus();
        order.setStatus(newStatus);
        ServiceOrder savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                savedOrder.getId(),
                savedOrder.getOrderCode(),
                oldStatus,
                newStatus,
                savedOrder.getCustomerId(),
                savedOrder.getDriverId(),
                changedByUserId,
                changedByRole,
                changedAt));

        return savedOrder;
    }
}
