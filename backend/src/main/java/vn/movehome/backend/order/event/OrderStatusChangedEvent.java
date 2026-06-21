package vn.movehome.backend.order.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Phat khi don doi trang thai.
 * M3: publish trong method transition (@Transactional).
 * M5: lang nghe bang @TransactionalEventListener(AFTER_COMMIT) de tao notification.
 */
public record OrderStatusChangedEvent(
        UUID orderId,
        String orderCode,
        String oldStatus,
        String newStatus,
        UUID customerId,
        UUID driverId,          // nullable: chua co tai xe
        UUID changedByUserId,   // ai gay transition (dung cho CANCELLED)
        String changedByRole,   // CUSTOMER / DRIVER / MANAGER / SYSTEM
        OffsetDateTime changedAt
) {}