package vn.movehome.backend.dispute;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerRefundService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal refundForDispute(
            UUID customerId,
            UUID orderId,
            UUID disputeId,
            BigDecimal amount,
            String description
    ) {
        BigDecimal normalizedAmount = money(amount);
        walletRepository.insertIfMissing(customerId);
        CustomerWallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "CUSTOMER_WALLET_NOT_FOUND|Khong tim thay vi khach hang."));

        BigDecimal balanceAfter = money(wallet.getBalance()).add(normalizedAmount).setScale(0);
        wallet.setBalance(balanceAfter);
        walletRepository.saveAndFlush(wallet);

        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(customerId)
                .type(TransactionType.REFUND)
                .amount(normalizedAmount)
                .relatedOrderId(orderId)
                .relatedDisputeId(disputeId)
                .balanceAfter(balanceAfter)
                .description(description)
                .build());

        return balanceAfter;
    }

    /**
     * Hoan coc cho khach khi Manager duyet yeu cau huy don (khong lien quan dispute).
     * Cong customer_wallet + ghi transaction(REFUND, related_order_id) trong CUNG transaction (AC-13, HR-18).
     * Chay trong transaction cua Manager (Propagation.MANDATORY). Idempotency dam bao boi guard trang thai
     * yeu cau (PENDING -> REFUNDED duoi lock) o ManagerCancellationRefundService.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal refundForCancellation(
            UUID customerId,
            UUID orderId,
            BigDecimal amount,
            String description
    ) {
        BigDecimal normalizedAmount = money(amount);
        walletRepository.insertIfMissing(customerId);
        CustomerWallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "CUSTOMER_WALLET_NOT_FOUND|Khong tim thay vi khach hang."));

        BigDecimal balanceAfter = money(wallet.getBalance()).add(normalizedAmount).setScale(0);
        wallet.setBalance(balanceAfter);
        walletRepository.saveAndFlush(wallet);

        transactionRepository.saveAndFlush(Transaction.builder()
                .userId(customerId)
                .type(TransactionType.REFUND)
                .amount(normalizedAmount)
                .relatedOrderId(orderId)
                .balanceAfter(balanceAfter)
                .description(description)
                .build());

        return balanceAfter;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly khong hop le.");
        }
        try {
            return value.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly phai la VND nguyen dong.");
        }
    }
}
