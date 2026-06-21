package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;

import java.util.LinkedHashSet;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.newStatus() == null) {
            log.warn("Bỏ qua event thiếu newStatus, orderId={}", event.orderId());
            return;
        }

        String orderCode = event.orderCode();
        switch (event.newStatus()) {
            case "ACCEPTED" -> notifyOne(
                    event.customerId(),
                    NotificationType.ORDER_ACCEPTED,
                    "Đơn đã được nhận",
                    "Đơn " + orderCode + " đã được tài xế nhận."
            );
            case "IN_PROGRESS" -> notifyOne(
                    event.customerId(),
                    NotificationType.ORDER_IN_PROGRESS,
                    "Đơn đang thực hiện",
                    "Đơn " + orderCode + " đang được vận chuyển."
            );
            case "COMPLETED" -> notifyOne(
                    event.customerId(),
                    NotificationType.ORDER_COMPLETED,
                    "Đơn đã hoàn thành",
                    "Đơn " + orderCode + " đã hoàn thành."
            );
            case "CANCELLED" -> notifyCancelled(event);
            default -> {
            }
        }
    }

    private void notifyCancelled(OrderStatusChangedEvent event) {
        String orderCode = event.orderCode();
        String title = "Đơn đã bị hủy";
        String message = "Đơn " + orderCode + " đã bị hủy.";

        LinkedHashSet<UUID> candidateUserIds = new LinkedHashSet<>();
        if (event.customerId() != null) {
            candidateUserIds.add(event.customerId());
        }
        if (event.driverId() != null) {
            candidateUserIds.add(event.driverId());
        }

        for (UUID userId : candidateUserIds) {
            if (!userId.equals(event.changedByUserId())) {
                notifyOne(userId, NotificationType.ORDER_CANCELLED, title, message);
            }
        }
    }

    private void notifyOne(UUID userId, String type, String title, String message) {
        if (userId == null) {
            return;
        }

        try {
            notificationService.create(userId, type, title, message);
        } catch (Exception ex) {
            log.error("Không thể tạo thông báo cho người dùng {}.", userId, ex);
        }
    }
}
