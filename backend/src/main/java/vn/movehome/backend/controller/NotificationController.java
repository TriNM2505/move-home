package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.notification.NotificationResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.NotificationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 10;
        }
        if (size > 100) {
            size = 100;
        }

        Pageable pageable = PageRequest.of(page, size);
        return notificationService.list(currentUserId(currentUser), pageable)
                .map(NotificationResponse::from);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        return NotificationResponse.from(notificationService.markRead(id, currentUserId(currentUser)));
    }

    private UUID currentUserId(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
        return currentUser.getId();
    }
}
