package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;
import vn.movehome.backend.repository.UserRepository;

import java.util.LinkedHashSet;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationListener {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.newStatus() == null) {
            log.warn("Bỏ qua event thiếu newStatus, orderId={}", event.orderId());
            return;
        }

        String orderCode = event.orderCode();
        switch (event.newStatus()) {
            case "CONFIRMED" -> notifyOne(
                    event.customerId(),
                    NotificationType.ORDER_CONFIRMED,
                    "Đơn đã xác nhận",
                    "Đơn " + orderCode + " đã được xác nhận thanh toán."
            );
            case "ASSIGNED" -> notifyOne(
                    event.customerId(),
                    NotificationType.ORDER_ASSIGNED,
                    "Đơn đã được phân công",
                    "Đơn " + orderCode + " đã được phân công tài xế."
            );
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
            case "DISPUTED", "IN_DISPUTE" -> notifyInDispute(event);
            default -> {
            }
        }
    }

    private void notifyInDispute(OrderStatusChangedEvent event) {
        String orderCode = event.orderCode();
        String title = "Đơn đang tranh chấp";
        String message = "Đơn " + orderCode + " đã được chuyển sang trạng thái tranh chấp.";

        LinkedHashSet<UUID> candidateUserIds = new LinkedHashSet<>();
        if (event.customerId() != null) {
            candidateUserIds.add(event.customerId());
        }
        if (event.driverId() != null) {
            candidateUserIds.add(event.driverId());
        }

        for (UUID userId : candidateUserIds) {
            notifyOne(userId, NotificationType.ORDER_IN_DISPUTE, title, message);
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
            log.error("Khong the tao thong bao cho nguoi dung {}.", userId, ex);
            return;
        }

        if (!shouldSendEmail(type)) {
            return;
        }

        try {
            userRepository.findById(userId).ifPresent(user -> {
                String email = user.getEmail();
                if (email == null || email.isBlank()) {
                    return;
                }
                emailService.send(email, title, message);
            });
        } catch (Exception ex) {
            log.error("Khong the gui email cho nguoi dung {}.", userId, ex);
        }
    }

    private boolean shouldSendEmail(String type) {
        return NotificationType.ORDER_ACCEPTED.equals(type)
                || NotificationType.ORDER_COMPLETED.equals(type)
                || NotificationType.ORDER_CANCELLED.equals(type);
    }
}
