package vn.movehome.backend.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.service.PaymentIdempotencyService;
import vn.movehome.backend.service.PaymentProcessingResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VnPayPaymentServiceTest {

    private static final String HASH_SECRET = "sandbox-secret";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private PaymentIdempotencyService paymentIdempotencyService;

    @Mock
    private OrderStatusTransitionService orderStatusTransitionService;

    private VnPaySigner signer;
    private VnPayPaymentService service;

    @BeforeEach
    void setUp() {
        VnPayProperties properties = new VnPayProperties();
        properties.setTmnCode("DEMO");
        properties.setHashSecret(HASH_SECRET);
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:8080/api/vnpay/return");
        properties.setIpnUrl("http://localhost:8080/api/vnpay/ipn");

        signer = new VnPaySigner();
        service = new VnPayPaymentService(
                properties,
                signer,
                orderRepository,
                walletRepository,
                userRepository,
                driverProfileRepository,
                paymentIdempotencyService,
                orderStatusTransitionService);
    }

    @Test
    void createOrderPaymentUrlBuildsSignedSandboxUrl() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH202606210001")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal("500000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        VnPayPaymentUrlResponse response = service.createOrderPaymentUrl(
                customerId,
                orderId,
                "203.0.113.10");

        // Khach chi tra coc 30% cua 500.000 = 150.000 (VNPay nhan don vi x100 = 15.000.000)
        assertThat(response.amount()).isEqualByComparingTo("150000");
        assertThat(response.txnRef()).startsWith("ORD-" + compact(orderId) + "-");
        assertThat(response.paymentUrl()).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(response.paymentUrl()).contains("vnp_Amount=15000000");
        assertThat(response.paymentUrl()).contains("vnp_SecureHash=");
    }

    @Test
    void successfulOrderIpnUpdatesOrderAndReturnsLedgerEntryOnce() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH202606210002")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal("750000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        AtomicReference<Transaction> ledger = new AtomicReference<>();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentIdempotencyService.process(eq(txnRef), any(PaymentIdempotencyService.PaymentOperation.class)))
                .thenAnswer(invocation -> {
                    PaymentIdempotencyService.PaymentOperation operation = invocation.getArgument(1);
                    Transaction transaction = operation.apply();
                    transaction.setId(UUID.randomUUID());
                    ledger.set(transaction);
                    return new PaymentProcessingResult(PaymentProcessingResult.Status.PROCESSED, transaction);
                });

        // Khach tra coc 30% cua 750.000 = 225.000
        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("225000")));

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(ledger.get().getType()).isEqualTo(TransactionType.ORDER_PAYMENT);
        assertThat(ledger.get().getAmount()).isEqualByComparingTo("225000");
        assertThat(ledger.get().getRelatedOrderId()).isEqualTo(orderId);
        // Chuyen PENDING_PAYMENT → CONFIRMED phai di qua transition service (audit + event)
        verify(orderStatusTransitionService).transition(
                eq(order), eq("CONFIRMED"), eq(customerId), eq("SYSTEM"), any());
    }

    @Test
    void createFinalPaymentUrlChargesRemainingSeventyPercent() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH202606210003")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        VnPayPaymentUrlResponse response = service.createFinalPaymentUrl(customerId, orderId, "203.0.113.10");

        // Con lai 70% cua 1.000.000 = 700.000 (VNPay x100 = 70.000.000)
        assertThat(response.amount()).isEqualByComparingTo("700000");
        assertThat(response.txnRef()).startsWith("OFP-" + compact(orderId) + "-");
        assertThat(response.paymentUrl()).contains("vnp_Amount=70000000");
    }

    @Test
    void successfulFinalIpnMarksFinalPaidWithoutChangingStatus() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String txnRef = "OFP-" + compact(orderId) + "-20260621213000-abcdef12";
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH202606210004")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        AtomicReference<Transaction> ledger = new AtomicReference<>();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentIdempotencyService.process(eq(txnRef), any(PaymentIdempotencyService.PaymentOperation.class)))
                .thenAnswer(invocation -> {
                    PaymentIdempotencyService.PaymentOperation operation = invocation.getArgument(1);
                    Transaction transaction = operation.apply();
                    transaction.setId(UUID.randomUUID());
                    ledger.set(transaction);
                    return new PaymentProcessingResult(PaymentProcessingResult.Status.PROCESSED, transaction);
                });

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("700000")));

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(order.getFinalPaidAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo("AWAITING_FINAL_PAYMENT"); // KHONG doi status
        assertThat(ledger.get().getAmount()).isEqualByComparingTo("700000");
        verify(orderStatusTransitionService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    void successfulWalletTopUpIpnCreditsWalletAndReturnsLedgerEntry() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        User customer = User.builder()
                .id(customerId)
                .role(UserRole.CUSTOMER)
                .email("customer@test.com")
                .fullName("Customer")
                .passwordHash("hash")
                .build();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("100000"))
                .totalToppedUp(new BigDecimal("100000"))
                .totalSpent(BigDecimal.ZERO)
                .build();
        AtomicReference<Transaction> ledger = new AtomicReference<>();

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        when(paymentIdempotencyService.process(eq(txnRef), any(PaymentIdempotencyService.PaymentOperation.class)))
                .thenAnswer(invocation -> {
                    PaymentIdempotencyService.PaymentOperation operation = invocation.getArgument(1);
                    Transaction transaction = operation.apply();
                    transaction.setId(UUID.randomUUID());
                    ledger.set(transaction);
                    return new PaymentProcessingResult(PaymentProcessingResult.Status.PROCESSED, transaction);
                });

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("500000")));

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(wallet.getBalance()).isEqualByComparingTo("600000");
        assertThat(wallet.getTotalToppedUp()).isEqualByComparingTo("600000");
        assertThat(ledger.get().getType()).isEqualTo(TransactionType.WALLET_TOP_UP);
        assertThat(ledger.get().getAmount()).isEqualByComparingTo("500000");
        verify(walletRepository).insertIfMissing(customerId);
        verify(walletRepository).save(wallet);
    }

    @Test
    void duplicateWalletIpnDoesNotCreditWalletAgain() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(customerId)
                .type(TransactionType.WALLET_TOP_UP)
                .amount(new BigDecimal("500000"))
                .vnpayTxnRef(txnRef)
                .build();

        when(paymentIdempotencyService.process(eq(txnRef), any(PaymentIdempotencyService.PaymentOperation.class)))
                .thenReturn(new PaymentProcessingResult(PaymentProcessingResult.Status.ALREADY_PROCESSED, existing));

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("500000")));

        assertThat(response.rspCode()).isEqualTo("00");
        verify(walletRepository, never()).insertIfMissing(any());
        verify(walletRepository, never()).save(any());
    }

    // ==================== createOrderPaymentUrl error paths ====================

    @Test
    void createOrderPaymentUrlThrowsNotFoundWhenOrderMissing() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrderPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("ORDER_NOT_FOUND|Khong tim thay don hang.");
                });
    }

    @Test
    void createOrderPaymentUrlThrowsConflictWhenOrderNotPayable() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("CONFIRMED")
                .totalQuote(new BigDecimal("500000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createOrderPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_ORDER_STATUS|Don hang khong o trang thai cho thanh toan.");
                });
    }

    @Test
    void createOrderPaymentUrlWrapsMissingDepositInputsAsPaymentException() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                .totalQuote(null)
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createOrderPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(VnPayPaymentException.class, ex -> {
                    assertThat(ex.rspCode()).isEqualTo("04");
                    assertThat(ex.getMessage()).isEqualTo("Invalid amount");
                });
    }

    // ==================== createFinalPaymentUrl error paths ====================

    @Test
    void createFinalPaymentUrlThrowsNotFoundWhenOrderMissing() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFinalPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createFinalPaymentUrlThrowsConflictWhenNotAwaitingFinalPayment() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createFinalPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_ORDER_STATUS|Don chua o buoc thanh toan not 70%.");
                });
    }

    @Test
    void createFinalPaymentUrlThrowsConflictWhenAlreadyPaid() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .finalPaidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createFinalPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("FINAL_ALREADY_PAID|Don da thanh toan not 70%.");
                });
    }

    @Test
    void createFinalPaymentUrlWrapsMissingTotalQuoteAsPaymentException() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(null)
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createFinalPaymentUrl(customerId, orderId, "203.0.113.10"))
                .isInstanceOfSatisfying(VnPayPaymentException.class, ex -> {
                    assertThat(ex.rspCode()).isEqualTo("04");
                    assertThat(ex.getMessage()).isEqualTo("Invalid amount");
                });
    }

    // ==================== createWalletTopUpUrl ====================

    @Test
    void createWalletTopUpUrlBuildsSignedUrlForValidAmount() {
        UUID customerId = UUID.randomUUID();

        VnPayPaymentUrlResponse response = service.createWalletTopUpUrl(
                customerId, new BigDecimal("300000"), "203.0.113.10");

        assertThat(response.amount()).isEqualByComparingTo("300000");
        assertThat(response.txnRef()).startsWith("WAL-" + compact(customerId) + "-");
        assertThat(response.paymentUrl()).contains("vnp_Amount=30000000");
    }

    @Test
    void createWalletTopUpUrlRejectsNullAmount() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createWalletTopUpUrl(customerId, null, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|So tien nap vi khong hop le.");
                });
    }

    @Test
    void createWalletTopUpUrlRejectsNonPositiveAmount() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createWalletTopUpUrl(customerId, BigDecimal.ZERO, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void createWalletTopUpUrlRejectsFractionalAmount() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() ->
                service.createWalletTopUpUrl(customerId, new BigDecimal("100.50"), "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void createWalletTopUpUrlRejectsAmountExceedingPrecision() {
        UUID customerId = UUID.randomUUID();

        // 16 chu so > NUMERIC(15,0)
        assertThatThrownBy(() -> service.createWalletTopUpUrl(
                customerId, new BigDecimal("1000000000000000"), "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void createWalletTopUpUrlThrowsConflictWhenVnPayNotConfigured() {
        VnPayProperties incompleteProperties = new VnPayProperties();
        incompleteProperties.setTmnCode("");
        incompleteProperties.setHashSecret(HASH_SECRET);
        incompleteProperties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        incompleteProperties.setReturnUrl("http://localhost:8080/api/vnpay/return");
        VnPayPaymentService misconfiguredService = new VnPayPaymentService(
                incompleteProperties,
                signer,
                orderRepository,
                walletRepository,
                userRepository,
                driverProfileRepository,
                paymentIdempotencyService,
                orderStatusTransitionService);

        assertThatThrownBy(() -> misconfiguredService.createWalletTopUpUrl(
                UUID.randomUUID(), new BigDecimal("100000"), "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("VNPAY_NOT_CONFIGURED|VNPay chua duoc cau hinh day du.");
                });
    }

    // ==================== createDriverDepositUrl ====================

    @Test
    void createDriverDepositUrlBuildsSignedUrlForFixedDeposit() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_DEPOSIT)
                .email("driver@test.com")
                .fullName("Driver")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

        VnPayPaymentUrlResponse response = service.createDriverDepositUrl(driverId, "203.0.113.10");

        assertThat(response.amount()).isEqualByComparingTo("3000000");
        assertThat(response.txnRef()).startsWith("DDP-" + compact(driverId) + "-");
        assertThat(response.paymentUrl()).contains("vnp_Amount=300000000");
    }

    @Test
    void createDriverDepositUrlThrowsNotFoundWhenDriverMissing() {
        UUID driverId = UUID.randomUUID();
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDriverDepositUrl(driverId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("DRIVER_NOT_FOUND|Khong tim thay tai xe.");
                });
    }

    @Test
    void createDriverDepositUrlThrowsNotFoundWhenUserIsNotDriver() {
        UUID driverId = UUID.randomUUID();
        User notDriver = User.builder()
                .id(driverId)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.PENDING_DEPOSIT)
                .email("c@test.com")
                .fullName("Customer")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(notDriver));

        assertThatThrownBy(() -> service.createDriverDepositUrl(driverId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDriverDepositUrlThrowsConflictWhenNotAtDepositStep() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_APPROVAL)
                .email("driver@test.com")
                .fullName("Driver")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> service.createDriverDepositUrl(driverId, "203.0.113.10"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_ONBOARDING_STEP|Chi co the dong coc sau khi da nop du giay to va truoc khi duoc duyet.");
                });
    }

    // ==================== handleReturn ====================

    @Test
    void handleReturnReportsInvalidSignature() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "WAL-abc");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", "tampered");

        VnPayReturnResponse response = service.handleReturn(params);

        assertThat(response.signatureValid()).isFalse();
        assertThat(response.successful()).isFalse();
        assertThat(response.txnRef()).isEqualTo("WAL-abc");
        assertThat(response.message()).isEqualTo("Chu ky VNPay khong hop le.");
        assertThat(response.processingStatus()).isNull();
    }

    @Test
    void handleReturnReportsPaymentFailure() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "WAL-abc");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayReturnResponse response = service.handleReturn(params);

        assertThat(response.signatureValid()).isTrue();
        assertThat(response.successful()).isFalse();
        assertThat(response.responseCode()).isEqualTo("24");
        assertThat(response.message()).isEqualTo("Thanh toan VNPay khong thanh cong.");
    }

    @Test
    void handleReturnMarksSuccessAndReportsProcessingStatus() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        User customer = User.builder()
                .id(customerId)
                .role(UserRole.CUSTOMER)
                .email("customer@test.com")
                .fullName("Customer")
                .passwordHash("hash")
                .build();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("100000"))
                .totalToppedUp(new BigDecimal("100000"))
                .totalSpent(BigDecimal.ZERO)
                .build();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        stubProcessInvokesOperation(txnRef);

        VnPayReturnResponse response = service.handleReturn(successCallback(txnRef, new BigDecimal("500000")));

        assertThat(response.signatureValid()).isTrue();
        assertThat(response.successful()).isTrue();
        assertThat(response.message()).isEqualTo("Thanh toan VNPay thanh cong.");
        assertThat(response.processingStatus()).isEqualTo("PROCESSED");
    }

    @Test
    void handleReturnThrowsConflictWhenCallbackRejected() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        assertThatThrownBy(() -> service.handleReturn(successCallback(txnRef, new BigDecimal("500000"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("VNPAY_CALLBACK_REJECTED|Customer not found");
                });
    }

    // ==================== handleIpn branch coverage ====================

    @Test
    void handleIpnReturnsInvalidChecksumOnBadSignature() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "WAL-abc");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", "tampered");

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("97");
        assertThat(response.message()).isEqualTo("Invalid Checksum");
    }

    @Test
    void handleIpnReturnsInvalidChecksumWhenTxnRefIsShortOrMissing() {
        Map<String, String> shortRefParams = new LinkedHashMap<>();
        shortRefParams.put("vnp_TxnRef", "ABC");
        shortRefParams.put("vnp_ResponseCode", "00");
        shortRefParams.put("vnp_SecureHash", "tampered");
        assertThat(service.handleIpn(shortRefParams).rspCode()).isEqualTo("97");

        Map<String, String> noRefParams = new LinkedHashMap<>();
        noRefParams.put("vnp_ResponseCode", "00");
        noRefParams.put("vnp_SecureHash", "tampered");
        assertThat(service.handleIpn(noRefParams).rspCode()).isEqualTo("97");
    }

    @Test
    void handleIpnIgnoresFailedPayment() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "WAL-abc");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(response.message()).isEqualTo("Payment Failed Ignored");
    }

    @Test
    void handleIpnIgnoresSuccessfulResponseCodeWithFailedTransactionStatus() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "WAL-abc");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(response.message()).isEqualTo("Payment Failed Ignored");
    }

    @Test
    void handleIpnReturnsUnknownErrorOnUnexpectedRuntimeException() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        when(paymentIdempotencyService.process(eq(txnRef), any(PaymentIdempotencyService.PaymentOperation.class)))
                .thenThrow(new IllegalStateException("boom"));

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("99");
        assertThat(response.message()).isEqualTo("Unknown error");
    }

    // ==================== processOrderPayment error branches (via IPN) ====================

    @Test
    void ipnRejectsOrderPaymentWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("150000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Order not found");
    }

    @Test
    void ipnRejectsOrderPaymentWhenOrderSoftDeleted() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal("500000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("150000")));

        assertThat(response.rspCode()).isEqualTo("01");
    }

    @Test
    void ipnRejectsOrderPaymentWhenAmountMismatch() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal("500000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        // Coc dung phai la 150.000, gui sai 999.000
        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("999000")));

        assertThat(response.rspCode()).isEqualTo("04");
        assertThat(response.message()).isEqualTo("Invalid amount");
    }

    @Test
    void ipnRejectsOrderPaymentWhenAlreadyConfirmed() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("CONFIRMED")
                .totalQuote(new BigDecimal("500000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("150000")));

        assertThat(response.rspCode()).isEqualTo("02");
        assertThat(response.message()).isEqualTo("Order already confirmed");
    }

    @Test
    void ipnRejectsOrderPaymentWhenOrderHasZeroDeposit() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                .totalQuote(BigDecimal.ZERO)
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("150000")));

        assertThat(response.rspCode()).isEqualTo("04");
    }

    @Test
    void ipnRejectsOrderPaymentWhenExpectedDepositExceedsMoneyPrecision() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                // 16 chu so, rate=1.0000 => deposit = chinh no, vuot NUMERIC(15,0)
                .totalQuote(new BigDecimal("9999999999999999"))
                .commissionRateSnapshot(new BigDecimal("1.0000"))
                .build();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("150000")));

        assertThat(response.rspCode()).isEqualTo("04");
        assertThat(response.message()).isEqualTo("Invalid amount");
    }

    // ==================== processOrderFinalPayment error branches (via IPN) ====================

    @Test
    void ipnRejectsFinalPaymentWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        String txnRef = "OFP-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("700000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Order not found");
    }

    @Test
    void ipnRejectsFinalPaymentWhenAmountMismatch() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        String txnRef = "OFP-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("999000")));

        assertThat(response.rspCode()).isEqualTo("04");
    }

    @Test
    void ipnRejectsFinalPaymentWhenNotAwaitingFinalPayment() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        String txnRef = "OFP-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("700000")));

        assertThat(response.rspCode()).isEqualTo("02");
        assertThat(response.message()).isEqualTo("Order not awaiting final payment");
    }

    @Test
    void ipnRejectsFinalPaymentWhenAlreadyPaid() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH1")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .finalPaidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        String txnRef = "OFP-" + compact(orderId) + "-20260621213000-abcdef12";
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("700000")));

        assertThat(response.rspCode()).isEqualTo("02");
        assertThat(response.message()).isEqualTo("Final payment already made");
    }

    // ==================== processWalletTopUp error branches (via IPN) ====================

    @Test
    void ipnRejectsWalletTopUpWhenCustomerNotFound() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Customer not found");
    }

    @Test
    void ipnRejectsWalletTopUpWhenUserIsNotCustomer() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        User driverUser = User.builder()
                .id(customerId)
                .role(UserRole.DRIVER)
                .email("d@test.com")
                .fullName("D")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(driverUser));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("01");
    }

    @Test
    void ipnRejectsWalletTopUpWhenWalletMissing() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        User customer = User.builder()
                .id(customerId)
                .role(UserRole.CUSTOMER)
                .email("c@test.com")
                .fullName("C")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Customer wallet not found");
    }

    @Test
    void ipnCreditsWalletTreatingNullBalancesAsZero() {
        UUID customerId = UUID.randomUUID();
        String txnRef = "WAL-" + compact(customerId) + "-20260621213000-abcdef12";
        User customer = User.builder()
                .id(customerId)
                .role(UserRole.CUSTOMER)
                .email("c@test.com")
                .fullName("C")
                .passwordHash("hash")
                .build();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(null)
                .totalToppedUp(null)
                .totalSpent(BigDecimal.ZERO)
                .build();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(wallet.getBalance()).isEqualByComparingTo("100000");
        assertThat(wallet.getTotalToppedUp()).isEqualByComparingTo("100000");
    }

    // ==================== processDriverDeposit branches (via IPN) ====================

    @Test
    void ipnProcessesDriverDepositAndAdvancesOnboarding() {
        UUID driverId = UUID.randomUUID();
        String txnRef = "DDP-" + compact(driverId) + "-20260621213000-abcdef12";
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_DEPOSIT)
                .email("driver@test.com")
                .fullName("Driver")
                .passwordHash("hash")
                .build();
        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("3000000")));

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(profile.getDepositAmount()).isEqualByComparingTo("3000000");
        assertThat(profile.getDepositPaidAt()).isNotNull();
        assertThat(driver.getStatus()).isEqualTo(UserStatus.PENDING_APPROVAL);
        verify(driverProfileRepository).save(profile);
        verify(userRepository).save(driver);
    }

    @Test
    void ipnRejectsDriverDepositWhenDriverNotFound() {
        UUID driverId = UUID.randomUUID();
        String txnRef = "DDP-" + compact(driverId) + "-20260621213000-abcdef12";
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("3000000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Driver not found");
    }

    @Test
    void ipnRejectsDriverDepositWhenAmountMismatch() {
        UUID driverId = UUID.randomUUID();
        String txnRef = "DDP-" + compact(driverId) + "-20260621213000-abcdef12";
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_DEPOSIT)
                .email("driver@test.com")
                .fullName("Driver")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("1000000")));

        assertThat(response.rspCode()).isEqualTo("04");
    }

    @Test
    void ipnRejectsDriverDepositWhenAlreadyProcessed() {
        UUID driverId = UUID.randomUUID();
        String txnRef = "DDP-" + compact(driverId) + "-20260621213000-abcdef12";
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_APPROVAL)
                .email("driver@test.com")
                .fullName("Driver")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("3000000")));

        assertThat(response.rspCode()).isEqualTo("02");
        assertThat(response.message()).isEqualTo("Driver deposit already processed");
    }

    @Test
    void ipnRejectsDriverDepositWhenProfileMissing() {
        UUID driverId = UUID.randomUUID();
        String txnRef = "DDP-" + compact(driverId) + "-20260621213000-abcdef12";
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_DEPOSIT)
                .email("driver@test.com")
                .fullName("Driver")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        stubProcessInvokesOperation(txnRef);

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("3000000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Driver profile not found");
    }

    // ==================== txnRef / amount parsing edge cases ====================

    @Test
    void ipnRejectsUnknownTxnRefPrefix() {
        String txnRef = "XXX-" + compact(UUID.randomUUID()) + "-20260621213000-abcdef12";

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Order not found");
    }

    @Test
    void ipnRejectsTxnRefWithoutEntityId() {
        String txnRef = "ORDONLY";

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("01");
    }

    @Test
    void ipnRejectsTxnRefWithMalformedCompactUuid() {
        String txnRef = "ORD-not-a-valid-uuid-part-20260621213000-abcdef12";

        VnPayIpnResponse response = service.handleIpn(successCallback(txnRef, new BigDecimal("100000")));

        assertThat(response.rspCode()).isEqualTo("01");
    }

    @Test
    void ipnRejectsNonNumericAmount() {
        UUID orderId = UUID.randomUUID();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", "not-a-number");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("04");
        assertThat(response.message()).isEqualTo("Invalid amount");
    }

    @Test
    void ipnRejectsAmountNotMultipleOfHundred() {
        UUID orderId = UUID.randomUUID();
        String txnRef = "ORD-" + compact(orderId) + "-20260621213000-abcdef12";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", "12345");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("04");
    }

    @Test
    void ipnRejectsWhenTxnRefParamMissing() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", "50000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("01");
        assertThat(response.message()).isEqualTo("Order not found");
    }

    @Test
    void ipnRejectsWhenAmountParamMissing() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "ORD-" + compact(UUID.randomUUID()) + "-20260621213000-abcdef12");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));

        VnPayIpnResponse response = service.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("04");
        assertThat(response.message()).isEqualTo("Invalid amount");
    }

    private void stubProcessInvokesOperation(String txnRef) {
        when(paymentIdempotencyService.process(eq(txnRef), any(PaymentIdempotencyService.PaymentOperation.class)))
                .thenAnswer(invocation -> {
                    PaymentIdempotencyService.PaymentOperation operation = invocation.getArgument(1);
                    Transaction transaction = operation.apply();
                    transaction.setId(UUID.randomUUID());
                    return new PaymentProcessingResult(PaymentProcessingResult.Status.PROCESSED, transaction);
                });
    }

    private Map<String, String> successCallback(String txnRef, BigDecimal amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", amount.multiply(new BigDecimal("100")).toPlainString());
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", signer.sign(params, HASH_SECRET));
        return params;
    }

    private String compact(UUID id) {
        return id.toString().replace("-", "");
    }
}
