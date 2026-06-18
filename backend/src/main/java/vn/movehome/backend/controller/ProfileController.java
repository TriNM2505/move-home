package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.customer.profile.ChangePasswordRequest;
import vn.movehome.backend.dto.customer.profile.ChangePasswordResponse;
import vn.movehome.backend.dto.customer.profile.CustomerProfileResponse;
import vn.movehome.backend.dto.customer.profile.UpdateProfileRequest;
import vn.movehome.backend.dto.customer.profile.UpdateProfileResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.CustomerProfileService;

@RestController
@RequestMapping("/api/customer/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CustomerProfileService customerProfileService;

    @GetMapping
    public CustomerProfileResponse getProfile(@AuthenticationPrincipal User currentUser) {
        return customerProfileService.getProfile(currentUser.getId());
    }

    @PutMapping
    public UpdateProfileResponse updateProfile(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UpdateProfileRequest request
    ) {
        return customerProfileService.updateProfile(currentUser.getId(), request);
    }

    @PutMapping("/password")
    public ChangePasswordResponse changePassword(
            @AuthenticationPrincipal User currentUser,
            @RequestBody ChangePasswordRequest request
    ) {
        return customerProfileService.changePassword(currentUser.getId(), request);
    }
}
