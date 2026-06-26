package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.UpdateUserStatusRequest;
import vn.movehome.backend.dto.admin.UserAccountStatusResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.AdminUserAccountService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserAccountController {

    private final AdminUserAccountService adminUserAccountService;

    @GetMapping("/{userId}/status")
    public UserAccountStatusResponse getStatus(@PathVariable UUID userId) {
        return adminUserAccountService.getStatus(userId);
    }

    @PatchMapping("/{userId}/status")
    public UserAccountStatusResponse updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal User actor
    ) {
        return adminUserAccountService.updateStatus(userId, request.status(), actor);
    }
}
