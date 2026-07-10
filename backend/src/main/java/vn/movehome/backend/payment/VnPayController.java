package vn.movehome.backend.payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.User;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
public class VnPayController {

    private final VnPayPaymentService vnpayPaymentService;
    private final String frontendReturnUrl;

    public VnPayController(
            VnPayPaymentService vnpayPaymentService,
            @Value("${app.frontend.vnpay-return-url:http://localhost:5500/frontend/pages/vnpay-return.html}")
            String frontendReturnUrl) {
        this.vnpayPaymentService = vnpayPaymentService;
        this.frontendReturnUrl = frontendReturnUrl;
    }

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

    /** Tao URL VNPay tra NOT 70% (don da AWAITING_FINAL_PAYMENT). */
    @PostMapping("/api/customer/orders/{orderId}/vnpay-final-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public VnPayPaymentUrlResponse createFinalPaymentUrl(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID orderId,
            HttpServletRequest request) {
        return vnpayPaymentService.createFinalPaymentUrl(
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

    /** Tao URL VNPay cho tai xe dong coc 3.000.000 VND (Onboarding Buoc 3, status PENDING_DEPOSIT). */
    @PostMapping("/api/driver/onboarding/deposit/vnpay")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.CREATED)
    public VnPayPaymentUrlResponse createDriverDepositUrl(
            @AuthenticationPrincipal User driver,
            HttpServletRequest request) {
        return vnpayPaymentService.createDriverDepositUrl(driver.getId(), clientIp(request));
    }

    /**
     * VNPay redirect trinh duyet khach ve day sau khi thanh toan.
     * Xu ly callback (verify + cap nhat don) roi REDIRECT sang trang ket qua FE thay vi tra JSON tho.
     * HR-03: return chi de hien thi; nguon cap nhat that su van la IPN (idempotent).
     */
    @GetMapping("/api/vnpay/return")
    public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
        String status;
        try {
            VnPayReturnResponse result = vnpayPaymentService.handleReturn(params);
            status = result.successful() ? "success" : "failed";
        } catch (ResponseStatusException exception) {
            status = "failed";
        }

        String txnRef = params.getOrDefault("vnp_TxnRef", "");
        String separator = frontendReturnUrl.contains("?") ? "&" : "?";
        String location = frontendReturnUrl + separator
                + "status=" + status
                + "&txn_ref=" + URLEncoder.encode(txnRef, StandardCharsets.UTF_8);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .build();
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
