package vn.movehome.backend.dto.notification;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.entity.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    @Test
    void fromMapsAllFieldsFromEntity() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");
        Notification notification = Notification.builder()
                .id(id)
                .userId(userId)
                .type("ORDER_COMPLETED")
                .title("Đơn đã hoàn thành")
                .message("Đơn ORD-001 đã hoàn thành.")
                .isRead(true)
                .createdAt(createdAt)
                .build();

        NotificationResponse response = NotificationResponse.from(notification);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.type()).isEqualTo("ORDER_COMPLETED");
        assertThat(response.title()).isEqualTo("Đơn đã hoàn thành");
        assertThat(response.message()).isEqualTo("Đơn ORD-001 đã hoàn thành.");
        assertThat(response.isRead()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void fromMapsUnreadNotificationWithoutIsReadFlagSet() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type("WITHDRAWAL_REQUESTED")
                .title("Yêu cầu rút tiền mới (khách hàng)")
                .message("Khách hàng A yêu cầu rút 100000 VND. Vui lòng xử lý.")
                .createdAt(OffsetDateTime.now())
                .build();

        NotificationResponse response = NotificationResponse.from(notification);

        assertThat(response.isRead()).isFalse();
        assertThat(response.message()).isEqualTo("Khách hàng A yêu cầu rút 100000 VND. Vui lòng xử lý.");
    }
}
