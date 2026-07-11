package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.customer.finance.CreateCustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalItemResponse;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestResponse;
import vn.movehome.backend.dto.customer.wallet.TransactionDTO;
import vn.movehome.backend.dto.customer.wallet.WalletSummaryDTO;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.WalletService;

@RestController
@RequestMapping("/api/customer/wallet")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletSummaryDTO getWallet(@AuthenticationPrincipal User currentUser) {
        return walletService.getOrCreateSummary(currentUser.getId());
    }

    @GetMapping("/transactions")
    public Page<TransactionDTO> getTransactions(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : walletService.defaultPageSize();
        return walletService.getTransactions(currentUser.getId(), page, pageSize);
    }

    // Khach hang tao yeu cau rut tien tu vi (Admin duyet thu cong sau).
    @PostMapping("/withdrawals")
    public ResponseEntity<CustomerWithdrawalRequestResponse> createWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateCustomerWithdrawalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.createWithdrawal(currentUser, request));
    }

    // Lich su yeu cau rut tien cua chinh khach hang (FE customer/withdrawal-history.html).
    @GetMapping("/withdrawals")
    public Page<CustomerWithdrawalItemResponse> getWithdrawals(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : walletService.defaultPageSize();
        return walletService.getWithdrawals(currentUser.getId(), page, pageSize);
    }
}
