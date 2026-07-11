package vn.movehome.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import vn.movehome.backend.dto.manager.DriverRatingItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.ManagerDriverRatingService;

/**
 * Danh gia tai xe cho Manager — xem duoc ca comment cua khach (khac Admin chi xem sao).
 * RBAC: /api/manager/** da duoc SecurityConfig gioi han hasRole('MANAGER') (HR-10).
 */
@RestController
@RequestMapping("/api/manager/driver-ratings")
@RequiredArgsConstructor
public class ManagerDriverRatingController {

    private final ManagerDriverRatingService ratingService;

    @GetMapping
    public Page<DriverRatingItem> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) Integer stars,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        requireAuthenticated(currentUser);
        return ratingService.search(driverId, stars, keyword, page, size);
    }

    private void requireAuthenticated(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
    }
}
