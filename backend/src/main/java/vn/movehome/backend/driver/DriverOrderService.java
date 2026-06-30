package vn.movehome.backend.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverOrderService {

    private static final String PENDING = "PENDING";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String ASSIGNED = "ASSIGNED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final Set<String> STARTABLE_STATUSES = Set.of(ACCEPTED, ASSIGNED);

    private final OrderRepository orderRepository;
    private final OrderStatusTransitionService orderStatusTransitionService;
    private final DriverEarningService driverEarningService;

    @Transactional
    public void acceptOrder(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);

        if (!PENDING.equals(order.getStatus()) || order.getDriverId() != null) {
            throw new IllegalStateException("Đơn không còn khả dụng");
        }

        OffsetDateTime changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        order.setDriverId(driverId);
        orderStatusTransitionService.transition(order, ACCEPTED, driverId, changedByRole, changedAt);

        logStateChange(driverId, orderId, PENDING, ACCEPTED, changedAt);
    }

    @Transactional
    public void startOrder(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);
        requireOwnership(order, driverId);

        String currentStatus = order.getStatus();
        if (!STARTABLE_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("Chỉ có thể bắt đầu đơn đã được chấp nhận");
        }

        OffsetDateTime changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        orderStatusTransitionService.transition(order, IN_PROGRESS, driverId, changedByRole, changedAt);

        logStateChange(driverId, orderId, currentStatus, IN_PROGRESS, changedAt);
    }

    @Transactional
    public void completeOrder(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);
        requireOwnership(order, driverId);

        if (!IN_PROGRESS.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hoàn thành đơn đang được thực hiện");
        }

        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        order.setCompletedAt(completedAt);
        ServiceOrder completedOrder = orderStatusTransitionService.transition(
                order, COMPLETED, driverId, changedByRole, completedAt);

        driverEarningService.creditEarning(completedOrder);
        logStateChange(driverId, orderId, IN_PROGRESS, COMPLETED, completedAt);
    }

    private ServiceOrder findForUpdate(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .filter(order -> order.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));
    }

    private void requireOwnership(ServiceOrder order, UUID driverId) {
        if (!driverId.equals(order.getDriverId())) {
            throw new AccessDeniedException("Bạn chỉ có thể thao tác đơn hàng của chính mình.");
        }
    }

    private void logStateChange(
            UUID driverId,
            UUID orderId,
            String fromState,
            String toState,
            OffsetDateTime timestamp
    ) {
        log.info(
                "order_state_audit actor_id={} actor_role=DRIVER timestamp={} from_state={} to_state={} entity_id={}",
                driverId,
                timestamp,
                fromState,
                toState,
                orderId);
    }
}
