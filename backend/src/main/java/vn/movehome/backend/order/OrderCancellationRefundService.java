package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.util.UUID;

/**
 * Tao yeu cau hoan coc khi khach huy don luc chua co tai xe (CONFIRMED) + bao Manager duyet.
 * Goi tu CustomerOrderActionService.cancelOrder SAU khi don da chuyen CANCELLED.
 * Manager xu ly o ManagerCancellationRefundService (hoan coc ve customer_wallet / tu choi).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationRefundService {

    private final OrderCancellationRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Mo yeu cau hoan coc PENDING cho don vua huy. Idempotent theo don (unique order_id).
     * Chay trong transaction cua cancelOrder (Propagation.MANDATORY).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void openForCancelledOrder(ServiceOrder order, UUID customerId, String reason) {
        // Chi tao khi don da tung coc (CONFIRMED) — kiem tra o CustomerOrderActionService truoc khi goi.
        if (refundRepository.existsByOrderId(order.getId())) {
            log.warn("openForCancelledOrder: don {} da co yeu cau hoan coc — bo qua", order.getId());
            return;
        }

        OrderCancellationRefund refund = OrderCancellationRefund.builder()
                .orderId(order.getId())
                .customerId(customerId)
                .reason(reason)
                .status(OrderCancellationRefund.STATUS_PENDING)
                .build();
        refundRepository.saveAndFlush(refund);

        // Bao Manager: tai dung NotificationType.ORDER_CANCELLED (khong phat sinh type moi can migration).
        notifyManagers(order.getOrderCode());

        log.info("cancellation_refund_opened actor_id={} order_id={} order_code={}",
                customerId, order.getId(), order.getOrderCode());
    }

    private void notifyManagers(String orderCode) {
        String title = "Yêu cầu hoàn cọc do hủy đơn";
        String message = "Đơn " + orderCode + " bị khách hủy khi chưa có tài xế — cần xem xét hoàn cọc.";
        userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE)
                .forEach(manager -> safeNotify(manager.getId(), title, message));
    }

    private void safeNotify(UUID userId, String title, String message) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.create(userId, NotificationType.ORDER_CANCELLED, title, message);
        } catch (Exception ex) {
            log.warn("Khong the tao notification hoan coc cho user {}: {}", userId, ex.getMessage());
        }
    }
}
