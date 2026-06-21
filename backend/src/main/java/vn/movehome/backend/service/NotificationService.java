package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(UUID userId, String type, String title, String message) {
        Objects.requireNonNull(userId, "Mã người dùng không được null.");
        Objects.requireNonNull(type, "Loại thông báo không được null.");
        Objects.requireNonNull(title, "Tiêu đề thông báo không được null.");
        Objects.requireNonNull(message, "Nội dung thông báo không được null.");

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public Notification markRead(UUID id, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND|Không tìm thấy thông báo."));

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return notification;
        }

        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }
}
