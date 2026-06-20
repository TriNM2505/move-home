package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.driver.onboarding.DriverProfileResponse;
import vn.movehome.backend.dto.driver.onboarding.UpdateDriverProfileRequest;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.DriverProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/driver/profile")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverProfileController {

    private final DriverProfileService driverProfileService;

    @GetMapping
    public DriverProfileResponse getProfile(@AuthenticationPrincipal User currentUser) {
        return driverProfileService.getProfile(currentUserId(currentUser));
    }

    @PutMapping
    public DriverProfileResponse updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateDriverProfileRequest request
    ) {
        return driverProfileService.updateProfile(currentUserId(currentUser), request);
    }

    @PostMapping("/submit")
    public DriverProfileResponse submitProfile(@AuthenticationPrincipal User currentUser) {
        return driverProfileService.submitProfile(currentUserId(currentUser));
    }

    private UUID currentUserId(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
        return currentUser.getId();
    }
}
