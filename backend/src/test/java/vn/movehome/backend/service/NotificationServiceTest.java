package vn.movehome.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createsNotificationWhenAllArgsValid() {
        UUID userId = UUID.randomUUID();
        String type = "ORDER_ASSIGNED";
        String title = "Order assigned";
        String message = "A new order has been assigned.";

        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.create(userId, type, title, message);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getType()).isEqualTo(type);
        assertThat(result.getTitle()).isEqualTo(title);
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getIsRead()).isFalse();
        assertThat(result.getCreatedAt()).isNotNull();
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void rejectsNullUserIdWhenCreating() {
        assertThatThrownBy(() -> notificationService.create(null, "ORDER_ASSIGNED", "Order assigned", "Message"))
                .isInstanceOf(NullPointerException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsNullTypeWhenCreating() {
        assertThatThrownBy(() -> notificationService.create(UUID.randomUUID(), null, "Order assigned", "Message"))
                .isInstanceOf(NullPointerException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsNullTitleWhenCreating() {
        assertThatThrownBy(() -> notificationService.create(UUID.randomUUID(), "ORDER_ASSIGNED", null, "Message"))
                .isInstanceOf(NullPointerException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsNullMessageWhenCreating() {
        assertThatThrownBy(() -> notificationService.create(UUID.randomUUID(), "ORDER_ASSIGNED", "Order assigned", null))
                .isInstanceOf(NullPointerException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void delegatesListToRepository() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> expectedPage = new PageImpl<>(
                List.of(buildNotification(UUID.randomUUID(), userId, false)));

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(expectedPage);

        Page<Notification> result = notificationService.list(userId, pageable);

        assertThat(result).isSameAs(expectedPage);
        verify(notificationRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Test
    void marksUnreadNotificationAsRead() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = buildNotification(id, userId, false);

        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.markRead(id, userId);

        assertThat(result).isSameAs(notification);
        assertThat(result.getIsRead()).isTrue();
        verify(notificationRepository, times(1)).findByIdAndUserId(id, userId);
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void returnsSameNotificationWhenAlreadyRead() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = buildNotification(id, userId, true);

        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(notification));

        Notification result = notificationService.markRead(id, userId);

        assertThat(result).isSameAs(notification);
        assertThat(result.getIsRead()).isTrue();
        verify(notificationRepository, times(1)).findByIdAndUserId(id, userId);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenNotificationMissing() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(id, userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NOTIFICATION_NOT_FOUND");
        verify(notificationRepository, times(1)).findByIdAndUserId(id, userId);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void treatsNullIsReadAsUnread() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = buildNotification(id, userId, null);

        when(notificationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.markRead(id, userId);

        assertThat(result).isSameAs(notification);
        assertThat(result.getIsRead()).isTrue();
        verify(notificationRepository, times(1)).findByIdAndUserId(id, userId);
        verify(notificationRepository, times(1)).save(notification);
    }

    private Notification buildNotification(UUID id, UUID userId, Boolean isRead) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type("ORDER_ASSIGNED")
                .title("Order assigned")
                .message("A new order has been assigned.")
                .isRead(isRead)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
