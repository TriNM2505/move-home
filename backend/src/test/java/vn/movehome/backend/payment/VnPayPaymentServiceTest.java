package vn.movehome.backend.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.service.PaymentIdempotencyService;
import vn.movehome.backend.service.PaymentProcessingResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
