package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
}
