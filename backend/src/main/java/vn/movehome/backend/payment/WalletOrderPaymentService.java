package vn.movehome.backend.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.order.OrderDepositCalculator;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.WalletRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Tra tien don bang so du Vi khach hang (thay cho VNPay):
 * - Coc 30% khi dat don (PENDING_PAYMENT → CONFIRMED)
 * - Tra not 70% (AWAITING_FINAL_PAYMENT, set final_paid_at)
 * Moi lan: kiem tra so du → tru vi → ghi transaction → cap nhat don, trong 1 transaction DB (HR-18, AC-13).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletOrderPaymentService {

    private static final Set<String> ORDER_PAYABLE_STATUSES = Set.of("PENDING", "PENDING_PAYMENT");
    private static final String CONFIRMED_STATUS = "CONFIRMED";
    private static final String AWAITING_FINAL_PAYMENT_STATUS = "AWAITING_FINAL_PAYMENT";

    private final OrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final OrderStatusTransitionService orderStatusTransitionService;

    /** Tra COC 30% tu vi → don ve CONFIRMED. */
    @Transactional
    public void payDepositFromWallet(UUID customerId, UUID orderId) {
        ServiceOrder order = loadOwnedOrder(customerId, orderId);

        if (!ORDER_PAYABLE_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATUS|Don hang khong o trang thai cho thanh toan.");
        }

        BigDecimal deposit = OrderDepositCalculator.deposit(order);
        debitWallet(customerId, order, deposit,
                "Thanh toan coc 30% don " + order.getOrderCode() + " tu vi");

        // PENDING_PAYMENT → CONFIRMED qua transition service (audit HR-13 + notification).
        orderStatusTransitionService.transition(
                order, CONFIRMED_STATUS, customerId, "CUSTOMER", OffsetDateTime.now(ZoneOffset.UTC));

        log.info("wallet_order_deposit_paid order_id={} deposit={}", orderId, deposit);
    }

    /** Tra NOT 70% tu vi → set final_paid_at (status van AWAITING_FINAL_PAYMENT cho tai xe hoan thanh). */
    @Transactional
    public void payFinalFromWallet(UUID customerId, UUID orderId) {
        ServiceOrder order = loadOwnedOrder(customerId, orderId);

        if (!AWAITING_FINAL_PAYMENT_STATUS.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATUS|Don chua o buoc thanh toan not 70%.");
        }
        if (order.getFinalPaidAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "FINAL_ALREADY_PAID|Don da thanh toan not 70%.");
        }

        BigDecimal remaining = OrderDepositCalculator.finalAmount(order);
        debitWallet(customerId, order, remaining,
                "Thanh toan not 70% don " + order.getOrderCode() + " tu vi");

        order.setFinalPaidAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(order);

        log.info("wallet_order_final_paid order_id={} amount={}", orderId, remaining);
    }

    private ServiceOrder loadOwnedOrder(UUID customerId, UUID orderId) {
        ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));
        // HR-10: khach chi thao tac don cua chinh minh
        if (!customerId.equals(order.getCustomerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ORDER_OWNERSHIP_REQUIRED|Ban chi co the thanh toan don cua minh.");
        }
        return order;
    }

    /**
     * Tru {@code amount} tu vi khach + ghi 1 transaction am (append-only, balance_after) trong cung transaction DB.
     * HR-18: chan truoc khi tru neu so du khong du (khong bao gio de balance am).
     */
    private void debitWallet(UUID customerId, ServiceOrder order, BigDecimal amount, String description) {
        walletRepository.insertIfMissing(customerId);
        CustomerWallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "WALLET_NOT_FOUND|Khong tim thay vi khach hang."));

        BigDecimal balance = nz(wallet.getBalance());
        if (balance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INSUFFICIENT_WALLET_BALANCE|Số dư ví không đủ. "
                            + "Vui lòng nạp thêm ví hoặc chọn thanh toán qua VNPay.");
        }

        BigDecimal newBalance = balance.subtract(amount);
        wallet.setBalance(newBalance);
        wallet.setTotalSpent(nz(wallet.getTotalSpent()).add(amount));
        walletRepository.save(wallet);

        Transaction ledger = Transaction.builder()
                .userId(customerId)
                .type(TransactionType.ORDER_PAYMENT)
                .amount(amount.negate())
                .relatedOrderId(order.getId())
                .balanceAfter(newBalance)
                .description(description)
                .build();
        transactionRepository.save(ledger);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
