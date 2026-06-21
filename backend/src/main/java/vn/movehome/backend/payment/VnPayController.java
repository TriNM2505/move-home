package vn.movehome.backend.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VnPayController {

    private final VnPayPaymentService vnpayPaymentService;

    @PostMapping("/api/customer/orders/{orderId}/vnpay-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public VnPayPaymentUrlResponse createOrderPaymentUrl(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID orderId,
            HttpServletRequest request) {
        return vnpayPaymentService.createOrderPaymentUrl(
                customer.getId(),
                orderId,
                clientIp(request));
    }

    @PostMapping("/api/customer/wallet/top-up/vnpay")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public VnPayPaymentUrlResponse createWalletTopUpUrl(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody CreateWalletTopUpRequest body,
            HttpServletRequest request) {
        return vnpayPaymentService.createWalletTopUpUrl(
                customer.getId(),
                body.amount(),
                clientIp(request));
    }

    @GetMapping("/api/vnpay/return")
    public VnPayReturnResponse handleReturn(@RequestParam Map<String, String> params) {
        return vnpayPaymentService.handleReturn(params);
    }

    @RequestMapping(value = "/api/vnpay/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public VnPayIpnResponse handleIpn(@RequestParam Map<String, String> params) {
        return vnpayPaymentService.handleIpn(params);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
