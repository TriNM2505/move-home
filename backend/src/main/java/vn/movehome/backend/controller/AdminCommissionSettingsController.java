package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.finance.CommissionSettingsResponse;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsRequest;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.CommissionSettingsService;

@RestController
@RequestMapping("/api/admin/settings/commission")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCommissionSettingsController {

    private final CommissionSettingsService commissionSettingsService;

    @GetMapping
    public CommissionSettingsResponse getCurrent() {
        return commissionSettingsService.getCurrent();
    }

    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH})
    public UpdateCommissionSettingsResponse update(
            @AuthenticationPrincipal User admin,
            @RequestBody UpdateCommissionSettingsRequest request
    ) {
        return commissionSettingsService.update(admin, request);
    }
}
