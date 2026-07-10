package vn.movehome.backend.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderDepositCalculator;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.service.PaymentIdempotencyService;
import vn.movehome.backend.service.PaymentProcessingResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayPaymentService {

    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final BigDecimal VNPAY_AMOUNT_MULTIPLIER = new BigDecimal("100");
    private static final int MAX_MONEY_PRECISION = 15;
    private static final int PAYMENT_EXPIRY_MINUTES = 15;

    private static final String ORDER_PREFIX = "ORD";
    private static final String FINAL_PREFIX = "OFP";
    private static final String WALLET_PREFIX = "WAL";
    private static final String DRIVER_DEPOSIT_PREFIX = "DDP";
    private static final String CONFIRMED_STATUS = "CONFIRMED";
    private static final String AWAITING_FINAL_PAYMENT_STATUS = "AWAITING_FINAL_PAYMENT";
    private static final Set<String> ORDER_PAYABLE_STATUSES = Set.of("PENDING", "PENDING_PAYMENT");
    // Coc tai xe co dinh 3.000.000 VND (CONTEXT §2 — collateral cho DamageReport)
    private static final BigDecimal DRIVER_DEPOSIT_AMOUNT = new BigDecimal("3000000");

    private final VnPayProperties properties;
    private final VnPaySigner signer;
    private final OrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final OrderStatusTransitionService orderStatusTransitionService;

    @Transactional(readOnly = true)
    public VnPayPaymentUrlResponse createOrderPaymentUrl(UUID customerId, UUID orderId, String ipAddress) {
        ServiceOrder order = orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));

        if (!ORDER_PAYABLE_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATUS|Don hang khong o trang thai cho thanh toan.");
        }

        // Khach chi tra COC 30% khi dat don (= commission cong ty), 70% tra not sau (CONTEXT §2)
        BigDecimal amount = normalizeApiMoney(computeDeposit(order), "So tien coc khong hop le.");
        String txnRef = newTxnRef(ORDER_PREFIX, order.getId());
        return buildPaymentUrl(
                txnRef,
                amount,
                "MoveHome deposit " + order.getOrderCode(),
                ipAddress);
    }

    /**
     * Tao URL VNPay tra NOT 70% (chi khi don da AWAITING_FINAL_PAYMENT va chua tra not).
     */
    @Transactional(readOnly = true)
    public VnPayPaymentUrlResponse createFinalPaymentUrl(UUID customerId, UUID orderId, String ipAddress) {
        ServiceOrder order = orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));

        if (!AWAITING_FINAL_PAYMENT_STATUS.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATUS|Don chua o buoc thanh toan not 70%.");
        }
        if (order.getFinalPaidAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "FINAL_ALREADY_PAID|Don da thanh toan not 70%.");
        }

        BigDecimal amount = normalizeApiMoney(finalAmount(order), "So tien con lai khong hop le.");
        String txnRef = newTxnRef(FINAL_PREFIX, order.getId());
        return buildPaymentUrl(
                txnRef,
                amount,
                "MoveHome final " + order.getOrderCode(),
                ipAddress);
    }

    public VnPayPaymentUrlResponse createWalletTopUpUrl(UUID customerId, BigDecimal amount, String ipAddress) {
        BigDecimal normalizedAmount = normalizeApiMoney(amount, "So tien nap vi khong hop le.");
        String txnRef = newTxnRef(WALLET_PREFIX, customerId);
        return buildPaymentUrl(
                txnRef,
                normalizedAmount,
                "MoveHome wallet topup " + compactUuid(customerId),
                ipAddress);
    }

    /**
     * Tao URL VNPay cho tai xe dong coc 3.000.000 VND (Onboarding Buoc 3).
     * Chi cho phep khi tai xe dang o trang thai PENDING_DEPOSIT (da nop du giay to).
     */
    @Transactional(readOnly = true)
    public VnPayPaymentUrlResponse createDriverDepositUrl(UUID driverId, String ipAddress) {
        User driver = userRepository.findById(driverId)
                .filter(user -> user.getRole() == UserRole.DRIVER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Khong tim thay tai xe."));

        if (driver.getStatus() != UserStatus.PENDING_DEPOSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ONBOARDING_STEP|Chi co the dong coc sau khi da nop du giay to va truoc khi duoc duyet.");
        }

        BigDecimal amount = normalizeApiMoney(DRIVER_DEPOSIT_AMOUNT, "So tien coc khong hop le.");
        String txnRef = newTxnRef(DRIVER_DEPOSIT_PREFIX, driverId);
        return buildPaymentUrl(
                txnRef,
                amount,
                "MoveHome driver deposit " + compactUuid(driverId),
                ipAddress);
    }

    public VnPayReturnResponse handleReturn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");

        if (!verifySignature(params)) {
            return new VnPayReturnResponse(
                    false,
                    false,
                    txnRef,
                    responseCode,
                    "Chu ky VNPay khong hop le.",
                    null);
        }

        if (!isPaymentSuccessful(params)) {
            return new VnPayReturnResponse(
                    true,
                    false,
                    txnRef,
                    responseCode,
                    "Thanh toan VNPay khong thanh cong.",
                    null);
        }

        try {
            PaymentProcessingResult result = processSuccessfulCallback(params);
            return new VnPayReturnResponse(
                    true,
                    true,
                    txnRef,
                    responseCode,
                    "Thanh toan VNPay thanh cong.",
                    result.status().name());
        } catch (VnPayPaymentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VNPAY_CALLBACK_REJECTED|" + exception.getMessage());
        }
    }

    public VnPayIpnResponse handleIpn(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");

        if (!verifySignature(params)) {
            log.warn("vnpay_ipn_invalid_signature txn_ref={}", maskTxnRef(txnRef));
            return new VnPayIpnResponse("97", "Invalid Checksum");
        }

        if (!isPaymentSuccessful(params)) {
            log.info("vnpay_ipn_payment_not_successful txn_ref={} response_code={} transaction_status={}",
                    maskTxnRef(txnRef),
                    params.get("vnp_ResponseCode"),
                    params.get("vnp_TransactionStatus"));
            return new VnPayIpnResponse("00", "Payment Failed Ignored");
        }

        try {
            processSuccessfulCallback(params);
            return new VnPayIpnResponse("00", "Confirm Success");
        } catch (VnPayPaymentException exception) {
            log.warn("vnpay_ipn_rejected txn_ref={} rsp_code={} message={}",
                    maskTxnRef(txnRef), exception.rspCode(), exception.getMessage());
            return new VnPayIpnResponse(exception.rspCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("vnpay_ipn_unhandled txn_ref={} message={}", maskTxnRef(txnRef), exception.getMessage(), exception);
            return new VnPayIpnResponse("99", "Unknown error");
        }
    }

    private VnPayPaymentUrlResponse buildPaymentUrl(
            String txnRef,
            BigDecimal amount,
            String orderInfo,
            String ipAddress) {
        requirePaymentConfig();

        ZonedDateTime now = ZonedDateTime.now(VNPAY_ZONE);
        ZonedDateTime expiresAt = now.plusMinutes(PAYMENT_EXPIRY_MINUTES);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.getTmnCode().trim());
        fields.put("vnp_Amount", toGatewayAmount(amount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", orderInfo);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.getReturnUrl().trim());
        fields.put("vnp_IpAddr", normalizeIpAddress(ipAddress));
        fields.put("vnp_CreateDate", VNPAY_DATE_FORMAT.format(now));
        fields.put("vnp_ExpireDate", VNPAY_DATE_FORMAT.format(expiresAt));
        if (hasText(properties.getIpnUrl())) {
            fields.put("vnp_IpnUrl", properties.getIpnUrl().trim());
        }

        String paymentUrl = signer.buildSignedUrl(
                properties.getPaymentUrl().trim(),
                fields,
                properties.getHashSecret().trim());
        return new VnPayPaymentUrlResponse(paymentUrl, txnRef, amount, expiresAt.toOffsetDateTime());
    }

    private PaymentProcessingResult processSuccessfulCallback(Map<String, String> params) {
        String txnRef = requireParam(params, "vnp_TxnRef", "01", "Order not found");
        ParsedTxnRef parsedTxnRef = parseTxnRef(txnRef);
        BigDecimal paidAmount = parseGatewayAmount(requireParam(params, "vnp_Amount", "04", "Invalid amount"));

        return switch (parsedTxnRef.target()) {
            case ORDER -> processOrderPayment(parsedTxnRef.entityId(), txnRef, paidAmount);
            case ORDER_FINAL -> processOrderFinalPayment(parsedTxnRef.entityId(), txnRef, paidAmount);
            case WALLET -> processWalletTopUp(parsedTxnRef.entityId(), txnRef, paidAmount);
            case DRIVER_DEPOSIT -> processDriverDeposit(parsedTxnRef.entityId(), txnRef, paidAmount);
        };
    }

    private PaymentProcessingResult processOrderPayment(UUID orderId, String txnRef, BigDecimal paidAmount) {
        return paymentIdempotencyService.process(txnRef, () -> {
            ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                    .filter(item -> item.getDeletedAt() == null)
                    .orElseThrow(() -> new VnPayPaymentException("01", "Order not found"));

            // Khach phai tra dung so tien COC 30% (khong phai 100% tong don)
            BigDecimal expectedAmount = normalizeCallbackMoney(computeDeposit(order));
            if (expectedAmount.compareTo(paidAmount) != 0) {
                throw new VnPayPaymentException("04", "Invalid amount");
            }

            if (!ORDER_PAYABLE_STATUSES.contains(order.getStatus())) {
                throw new VnPayPaymentException("02", "Order already confirmed");
            }

            // Chuyen PENDING_PAYMENT → CONFIRMED qua transition service:
            // ghi audit (HR-13) + phat OrderStatusChangedEvent → notification (HR-05).
            // Actor la SYSTEM vi cap nhat den tu IPN server-to-server (CONTEXT §2).
            orderStatusTransitionService.transition(
                    order,
                    CONFIRMED_STATUS,
                    order.getCustomerId(),
                    "SYSTEM",
                    OffsetDateTime.now(ZoneOffset.UTC));

            return Transaction.builder()
                    .userId(order.getCustomerId())
                    .type(TransactionType.ORDER_PAYMENT)
                    .amount(paidAmount)
                    .relatedOrderId(order.getId())
                    .description("Thanh toan coc 30% don " + order.getOrderCode() + " qua VNPay")
                    .build();
        });
    }

    private PaymentProcessingResult processOrderFinalPayment(UUID orderId, String txnRef, BigDecimal paidAmount) {
        return paymentIdempotencyService.process(txnRef, () -> {
            ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                    .filter(item -> item.getDeletedAt() == null)
                    .orElseThrow(() -> new VnPayPaymentException("01", "Order not found"));

            // Khach phai tra dung so tien con lai 70%
            BigDecimal expectedAmount = normalizeCallbackMoney(finalAmount(order));
            if (expectedAmount.compareTo(paidAmount) != 0) {
                throw new VnPayPaymentException("04", "Invalid amount");
            }

            if (!AWAITING_FINAL_PAYMENT_STATUS.equals(order.getStatus())) {
                throw new VnPayPaymentException("02", "Order not awaiting final payment");
            }
            if (order.getFinalPaidAt() != null) {
                throw new VnPayPaymentException("02", "Final payment already made");
            }

            // Danh dau da tra not 70%; KHONG doi status (van AWAITING_FINAL_PAYMENT cho tai xe bam Hoan thanh).
            order.setFinalPaidAt(OffsetDateTime.now(ZoneOffset.UTC));
            orderRepository.save(order);

            return Transaction.builder()
                    .userId(order.getCustomerId())
                    .type(TransactionType.ORDER_PAYMENT)
                    .amount(paidAmount)
                    .relatedOrderId(order.getId())
                    .description("Thanh toan not 70% don " + order.getOrderCode() + " qua VNPay")
                    .build();
        });
    }

    private PaymentProcessingResult processWalletTopUp(UUID customerId, String txnRef, BigDecimal paidAmount) {
        return paymentIdempotencyService.process(txnRef, () -> {
            User customer = userRepository.findById(customerId)
                    .filter(user -> user.getRole() == UserRole.CUSTOMER)
                    .orElseThrow(() -> new VnPayPaymentException("01", "Customer not found"));

            walletRepository.insertIfMissing(customer.getId());
            CustomerWallet wallet = walletRepository.findByCustomerIdForUpdate(customer.getId())
                    .orElseThrow(() -> new VnPayPaymentException("01", "Customer wallet not found"));

            wallet.setBalance(normalizeNullableMoney(wallet.getBalance()).add(paidAmount));
            wallet.setTotalToppedUp(normalizeNullableMoney(wallet.getTotalToppedUp()).add(paidAmount));
            walletRepository.save(wallet);

            return Transaction.builder()
                    .userId(customer.getId())
                    .type(TransactionType.WALLET_TOP_UP)
                    .amount(paidAmount)
                    .description("Nap vi khach qua VNPay")
                    .build();
        });
    }

    /**
     * Xu ly IPN coc tai xe: cong coc 3M vao driver_profile, chuyen PENDING_DEPOSIT → PENDING_APPROVAL
     * (FR-061). Idempotent qua PaymentIdempotencyService (HR-15).
     */
    private PaymentProcessingResult processDriverDeposit(UUID driverId, String txnRef, BigDecimal paidAmount) {
        return paymentIdempotencyService.process(txnRef, () -> {
            User driver = userRepository.findById(driverId)
                    .filter(user -> user.getRole() == UserRole.DRIVER)
                    .orElseThrow(() -> new VnPayPaymentException("01", "Driver not found"));

            // Coc phai dung 3.000.000 VND
            BigDecimal expectedAmount = normalizeCallbackMoney(DRIVER_DEPOSIT_AMOUNT);
            if (expectedAmount.compareTo(paidAmount) != 0) {
                throw new VnPayPaymentException("04", "Invalid amount");
            }

            if (driver.getStatus() != UserStatus.PENDING_DEPOSIT) {
                throw new VnPayPaymentException("02", "Driver deposit already processed");
            }

            DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                    .orElseThrow(() -> new VnPayPaymentException("01", "Driver profile not found"));

            // Ghi coc vao ho so + chuyen sang cho Manager duyet
            profile.setDepositAmount(paidAmount);
            profile.setDepositPaidAt(OffsetDateTime.now(ZoneOffset.UTC));
            driverProfileRepository.save(profile);

            driver.setStatus(UserStatus.PENDING_APPROVAL);
            userRepository.save(driver);

            return Transaction.builder()
                    .userId(driver.getId())
                    .type(TransactionType.DEPOSIT_TOP_UP)
                    .amount(paidAmount)
                    .description("Tai xe dong coc 3.000.000 VND qua VNPay")
                    .build();
        });
    }

    private boolean verifySignature(Map<String, String> params) {
        if (!hasText(properties.getHashSecret())) {
            log.error("vnpay_hash_secret_missing");
            return false;
        }
        return signer.verify(params, properties.getHashSecret().trim());
    }

    private boolean isPaymentSuccessful(Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        return "00".equals(responseCode) && (transactionStatus == null || "00".equals(transactionStatus));
    }

    private String requireParam(Map<String, String> params, String name, String rspCode, String message) {
        String value = params.get(name);
        if (!hasText(value)) {
            throw new VnPayPaymentException(rspCode, message);
        }
        return value.trim();
    }

    private BigDecimal parseGatewayAmount(String rawAmount) {
        try {
            BigDecimal minorUnits = new BigDecimal(rawAmount).setScale(0, RoundingMode.UNNECESSARY);
            if (minorUnits.remainder(VNPAY_AMOUNT_MULTIPLIER).signum() != 0) {
                throw new ArithmeticException("amount is not VND x 100");
            }
            return normalizeCallbackMoney(minorUnits.divide(VNPAY_AMOUNT_MULTIPLIER));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new VnPayPaymentException("04", "Invalid amount");
        }
    }

    private ParsedTxnRef parseTxnRef(String txnRef) {
        String[] parts = txnRef.split("-", 4);
        if (parts.length < 2) {
            throw new VnPayPaymentException("01", "Order not found");
        }

        CallbackTarget target = switch (parts[0]) {
            case ORDER_PREFIX -> CallbackTarget.ORDER;
            case FINAL_PREFIX -> CallbackTarget.ORDER_FINAL;
            case WALLET_PREFIX -> CallbackTarget.WALLET;
            case DRIVER_DEPOSIT_PREFIX -> CallbackTarget.DRIVER_DEPOSIT;
            default -> throw new VnPayPaymentException("01", "Order not found");
        };

        return new ParsedTxnRef(target, uuidFromCompact(parts[1]));
    }

    private UUID uuidFromCompact(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{32}")) {
            throw new VnPayPaymentException("01", "Order not found");
        }
        return UUID.fromString(
                value.substring(0, 8) + "-"
                        + value.substring(8, 12) + "-"
                        + value.substring(12, 16) + "-"
                        + value.substring(16, 20) + "-"
                        + value.substring(20));
    }

    private String newTxnRef(String prefix, UUID id) {
        String randomSuffix = compactUuid(UUID.randomUUID()).substring(0, 8);
        return prefix + "-" + compactUuid(id) + "-" + VNPAY_DATE_FORMAT.format(ZonedDateTime.now(VNPAY_ZONE))
                + "-" + randomSuffix;
    }

    private String compactUuid(UUID id) {
        return id.toString().replace("-", "");
    }

    private String toGatewayAmount(BigDecimal amount) {
        return amount.multiply(VNPAY_AMOUNT_MULTIPLIER)
                .setScale(0, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    /**
     * Tinh so tien coc 30% (nguon chung OrderDepositCalculator). Phan 70% con lai tra sau.
     */
    private BigDecimal computeDeposit(ServiceOrder order) {
        try {
            return OrderDepositCalculator.deposit(order);
        } catch (IllegalStateException exception) {
            throw new VnPayPaymentException("04", "Invalid amount");
        }
    }

    /** So tien con lai 70% (nguon chung OrderDepositCalculator). */
    private BigDecimal finalAmount(ServiceOrder order) {
        try {
            return OrderDepositCalculator.finalAmount(order);
        } catch (IllegalStateException exception) {
            throw new VnPayPaymentException("04", "Invalid amount");
        }
    }

    private BigDecimal normalizeApiMoney(BigDecimal amount, String message) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR|" + message);
        }
        try {
            BigDecimal normalized = amount.setScale(0, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > MAX_MONEY_PRECISION) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR|" + message);
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR|" + message);
        }
    }

    private BigDecimal normalizeCallbackMoney(BigDecimal amount) {
        try {
            BigDecimal normalized = amount.setScale(0, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > MAX_MONEY_PRECISION) {
                throw new ArithmeticException("amount out of range");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new VnPayPaymentException("04", "Invalid amount");
        }
    }

    private BigDecimal normalizeNullableMoney(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(0);
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    private void requirePaymentConfig() {
        if (!hasText(properties.getTmnCode())
                || !hasText(properties.getHashSecret())
                || !hasText(properties.getPaymentUrl())
                || !hasText(properties.getReturnUrl())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VNPAY_NOT_CONFIGURED|VNPay chua duoc cau hinh day du.");
        }
    }

    private String normalizeIpAddress(String ipAddress) {
        return hasText(ipAddress) ? ipAddress.trim() : "127.0.0.1";
    }

    private String maskTxnRef(String txnRef) {
        if (!hasText(txnRef)) {
            return null;
        }
        String trimmed = txnRef.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum CallbackTarget {
        ORDER,
        ORDER_FINAL,
        WALLET,
        DRIVER_DEPOSIT
    }

    private record ParsedTxnRef(CallbackTarget target, UUID entityId) {
    }
}
