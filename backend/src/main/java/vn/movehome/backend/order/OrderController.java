package vn.movehome.backend.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.payment.WalletOrderPaymentService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CustomerOrderActionService customerOrderActionService;
    private final CustomerOrderQueryService customerOrderQueryService;
    private final WalletOrderPaymentService walletOrderPaymentService;

    /**
     * Danh sach don cua chinh Customer dang dang nhap (HR-10 — scope theo JWT principal).
     * scope=pending (mac dinh): don chua ket thuc. scope=history: don COMPLETED/CANCELLED.
     */
    @GetMapping("/api/customer/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<CustomerOrderItem> getMyOrders(
            @AuthenticationPrincipal User customer,
            @RequestParam(defaultValue = "pending") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customerOrderQueryService.getMyOrders(
                customer.getId(), scope, PageRequest.of(page, size));
    }

    /**
     * Chi tiet 1 don cua chinh Customer (HR-10 — scope theo JWT principal),
     * kem thong tin coc 30% da tra / con lai 70%.
     */
    @GetMapping("/api/customer/orders/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerOrderDetailResponse getOrderDetail(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        return customerOrderQueryService.getOrderDetail(customer.getId(), id);
    }

    /** Hình tuyến đường thật (điểm đón → điểm trả) để vẽ trên bản đồ theo dõi của khách. */
    @GetMapping("/api/customer/orders/{id}/route")
    @PreAuthorize("hasRole('CUSTOMER')")
    public java.util.List<double[]> getOrderRoute(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        return customerOrderQueryService.getOrderRoute(customer.getId(), id);
    }

    @PostMapping("/api/customer/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(customer.getId(), request);
    }

    @PutMapping("/api/customer/orders/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CancelOrderResponse cancelOrder(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id,
            @RequestBody CancelOrderRequest request) {
        return customerOrderActionService.cancelOrder(
                customer.getId(), customer.getRole().name(), id, request);
    }

    /**
     * Anh xac thuc tai xe (chan dung + anh xe) cua don da co tai xe nhan — de khach doi chieu.
     */
    @GetMapping("/api/customer/orders/{id}/driver-verification")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerDriverVerificationResponse getDriverVerification(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        return customerOrderQueryService.getDriverVerification(customer.getId(), id);
    }

    /**
     * Khach bao cao tai xe/xe khong khop anh xac thuc → huy chuyen (chi khi don dang ACCEPTED).
     */
    @PutMapping("/api/customer/orders/{id}/report-mismatch")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CancelOrderResponse reportDriverMismatch(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        return customerOrderActionService.reportDriverMismatch(
                customer.getId(), customer.getRole().name(), id);
    }

    /**
     * Khach xac nhan tai xe/xe DUNG voi anh (sau khi tai xe da den diem don) → bat dau chuyen (IN_PROGRESS).
     */
    @PutMapping("/api/customer/orders/{id}/confirm-driver")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CancelOrderResponse confirmDriverMatch(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        return customerOrderActionService.confirmDriverMatch(
                customer.getId(), customer.getRole().name(), id);
    }

    @PostMapping("/api/customer/orders/{id}/rating")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse rateOrder(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id,
            @RequestBody RatingRequest request) {
        return customerOrderActionService.rateOrder(customer.getId(), id, request);
    }

    /** Danh gia da gui cua don (404 RATING_NOT_FOUND neu chua danh gia) — FE hien trang thai nut. */
    @GetMapping("/api/customer/orders/{id}/rating")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RatingDetailResponse getOrderRating(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        return customerOrderActionService.getOrderRating(customer.getId(), id);
    }

    /**
     * Tra COC 30% cua don bang so du Vi (thay cho VNPay). Thanh cong → don ve CONFIRMED.
     * HR-10: chi tra coc cho don cua chinh minh (kiem tra trong service).
     */
    @PostMapping("/api/customer/orders/{id}/wallet-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    public void payDepositFromWallet(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        walletOrderPaymentService.payDepositFromWallet(customer.getId(), id);
    }

    /**
     * Tra NOT 70% cua don bang so du Vi (khi don da AWAITING_FINAL_PAYMENT).
     */
    @PostMapping("/api/customer/orders/{id}/wallet-final-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    public void payFinalFromWallet(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id) {
        walletOrderPaymentService.payFinalFromWallet(customer.getId(), id);
    }
}
