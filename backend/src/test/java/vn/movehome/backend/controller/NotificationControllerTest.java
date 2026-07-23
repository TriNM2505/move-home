package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.notification.NotificationResponse;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.NotificationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    private final NotificationService service = mock(NotificationService.class);
    private final NotificationController controller = new NotificationController(service);

    @Test
    void listUsesRequestedPageAndSize() {
        UUID userId = UUID.randomUUID();
        User currentUser = User.builder().id(userId).build();
        Notification notification = notification(userId);
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(service.list(eq(userId), any(Pageable.class))).thenReturn(page);

        Page<NotificationResponse> result = controller.list(currentUser, 1, 25);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(notification.getId());
        verify(service).list(userId, PageRequest.of(1, 25));
    }

    @Test
    void listClampsNegativePageToZero() {
        UUID userId = UUID.randomUUID();
        User currentUser = User.builder().id(userId).build();
        when(service.list(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list(currentUser, -5, 10);

        verify(service).list(userId, PageRequest.of(0, 10));
    }

    @Test
    void listClampsSizeBelowOneToDefaultTen() {
        UUID userId = UUID.randomUUID();
        User currentUser = User.builder().id(userId).build();
        when(service.list(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list(currentUser, 0, 0);

        verify(service).list(userId, PageRequest.of(0, 10));
    }

    @Test
    void listClampsSizeAboveHundredToHundred() {
        UUID userId = UUID.randomUUID();
        User currentUser = User.builder().id(userId).build();
        when(service.list(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list(currentUser, 0, 500);

        verify(service).list(userId, PageRequest.of(0, 100));
    }

    @Test
    void listThrowsUnauthorizedWhenPrincipalMissing() {
        assertThatThrownBy(() -> controller.list(null, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục."));
    }

    @Test
    void markReadDelegatesToServiceAndMapsResponse() {
        UUID userId = UUID.randomUUID();
        User currentUser = User.builder().id(userId).build();
        UUID notificationId = UUID.randomUUID();
        Notification notification = notification(userId);
        notification.setId(notificationId);
        when(service.markRead(notificationId, userId)).thenReturn(notification);

        NotificationResponse response = controller.markRead(currentUser, notificationId);

        assertThat(response.id()).isEqualTo(notificationId);
        verify(service).markRead(notificationId, userId);
    }

    @Test
    void markReadThrowsUnauthorizedWhenPrincipalMissing() {
        UUID notificationId = UUID.randomUUID();
        assertThatThrownBy(() -> controller.markRead(null, notificationId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục."));
    }

    private Notification notification(UUID userId) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type("ORDER_ACCEPTED")
                .title("Đơn đã được nhận")
                .message("Đơn ORD-001 đã được tài xế nhận.")
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
