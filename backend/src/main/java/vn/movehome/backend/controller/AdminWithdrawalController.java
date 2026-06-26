package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.finance.PendingWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.AdminWithdrawalService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/withdrawals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWithdrawalController {

    private final AdminWithdrawalService adminWithdrawalService;

    @GetMapping("/pending")
    public PendingWithdrawalPageResponse getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : adminWithdrawalService.defaultPageSize();
        return adminWithdrawalService.getPending(page, pageSize);
    }

    @PostMapping({"/{withdrawalId}/process", "/{withdrawalId}/approve"})
    public WithdrawalActionResponse process(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal User admin,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProcessWithdrawalRequest request
    ) {
        return adminWithdrawalService.process(withdrawalId, admin, request);
    }

    @PostMapping("/{withdrawalId}/reject")
    public WithdrawalActionResponse reject(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal User admin,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RejectWithdrawalRequest request
    ) {
        return adminWithdrawalService.reject(withdrawalId, admin, request);
    }
}
