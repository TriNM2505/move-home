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
import vn.movehome.backend.dto.admin.finance.PendingCustomerWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.AdminCustomerWithdrawalService;

import java.util.UUID;

// Admin duyet/tu choi yeu cau rut tien cua khach hang (HR-10: chi ADMIN).
@RestController
@RequestMapping("/api/admin/customer-withdrawals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCustomerWithdrawalController {

    private final AdminCustomerWithdrawalService adminCustomerWithdrawalService;

    @GetMapping("/pending")
    public PendingCustomerWithdrawalPageResponse getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : adminCustomerWithdrawalService.defaultPageSize();
        return adminCustomerWithdrawalService.getPending(page, pageSize);
    }

    @PostMapping({"/{withdrawalId}/process", "/{withdrawalId}/approve"})
    public WithdrawalActionResponse process(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal User admin,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProcessWithdrawalRequest request
    ) {
        return adminCustomerWithdrawalService.process(withdrawalId, admin, request);
    }

    @PostMapping("/{withdrawalId}/reject")
    public WithdrawalActionResponse reject(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal User admin,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RejectWithdrawalRequest request
    ) {
        return adminCustomerWithdrawalService.reject(withdrawalId, admin, request);
    }
}
