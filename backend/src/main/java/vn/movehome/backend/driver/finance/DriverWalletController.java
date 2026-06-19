package vn.movehome.backend.driver.finance;

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
import vn.movehome.backend.entity.User;

@RestController
@RequestMapping("/api/driver")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverWalletController {

    private final DriverEarningService driverEarningService;

    @GetMapping("/wallet")
    public DriverWalletSummaryResponse getWallet(@AuthenticationPrincipal User currentUser) {
        return driverEarningService.getWallet(currentUser.getId());
    }

    @GetMapping("/earnings")
    public Page<DriverEarningResponse> getEarnings(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : driverEarningService.defaultPageSize();
        return driverEarningService.getEarnings(currentUser.getId(), page, pageSize);
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<WithdrawalRequestResponse> createWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverEarningService.createWithdrawal(currentUser, request));
    }
}
