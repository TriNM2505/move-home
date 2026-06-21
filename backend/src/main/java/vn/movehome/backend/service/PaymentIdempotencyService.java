package vn.movehome.backend.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Dam bao moi vnpay_txn_ref chi thay doi vi/don va ghi so mot lan (HR-15).
 *
 * PaymentOperation chi duoc thay doi du lieu trong DB. Tat ca thay doi do va INSERT vao
 * bang transaction chay trong cung mot transaction REQUIRES_NEW. UNIQUE(vnpay_txn_ref)
 * trong V6 la lop bao ve cuoi cung khi hai IPN cung den dong thoi: transaction thua se
 * rollback toan bo thay doi vi/don va tra ve ban ghi da duoc xu ly truoc do.
 */
@Service
public class PaymentIdempotencyService {

    private static final int MAX_VNPAY_TXN_REF_LENGTH = 100;
    private static final int MAX_MONEY_PRECISION = 15;

    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentIdempotencyService(
            TransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager) {
        this.transactionRepository = transactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Xu ly mot IPN VNPay theo nguyen tac exactly-once o DB.
     *
     * @param vnpayTxnRef ma giao dich VNPay, khong rong va toi da 100 ky tu
     * @param operation   cap nhat vi/don va tra ve mot Transaction moi chua duoc persist
     * @return ban ghi vua tao, hoac chinh ban ghi cu neu ref da duoc xu ly
     */
    public PaymentProcessingResult process(String vnpayTxnRef, PaymentOperation operation) {
        String normalizedTxnRef = normalizeTxnRef(vnpayTxnRef);
        Objects.requireNonNull(operation, "operation must not be null");

        Optional<Transaction> existing = transactionRepository.findByVnpayTxnRef(normalizedTxnRef);
        if (existing.isPresent()) {
            return alreadyProcessed(existing.get());
        }

        try {
            PaymentProcessingResult result = transactionTemplate.execute(status ->
                    processInTransaction(normalizedTxnRef, operation));
            return Objects.requireNonNull(result, "payment transaction returned no result");
        } catch (DataIntegrityViolationException exception) {
            // saveAndFlush/commit doi transaction canh tranh ket thuc truoc khi nem UNIQUE violation.
            return transactionRepository.findByVnpayTxnRef(normalizedTxnRef)
                    .map(this::alreadyProcessed)
                    .orElseThrow(() -> exception);
        }
    }

    private PaymentProcessingResult processInTransaction(
            String vnpayTxnRef,
            PaymentOperation operation) {
        Optional<Transaction> existing = transactionRepository.findByVnpayTxnRef(vnpayTxnRef);
        if (existing.isPresent()) {
            return alreadyProcessed(existing.get());
        }

        Transaction ledgerEntry = Objects.requireNonNull(
                operation.apply(),
                "operation must return a transaction");
        prepareNewLedgerEntry(ledgerEntry, vnpayTxnRef);

        Transaction saved = transactionRepository.saveAndFlush(ledgerEntry);
        return new PaymentProcessingResult(PaymentProcessingResult.Status.PROCESSED, saved);
    }

    private void prepareNewLedgerEntry(Transaction ledgerEntry, String vnpayTxnRef) {
        if (ledgerEntry.getId() != null) {
            throw new IllegalArgumentException("operation must return a new append-only transaction");
        }

        BigDecimal amount = Objects.requireNonNull(ledgerEntry.getAmount(), "amount must not be null");
        try {
            amount = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("amount must be an integer VND value", exception);
        }
        if (amount.precision() > MAX_MONEY_PRECISION) {
            throw new IllegalArgumentException("amount exceeds NUMERIC(15,0)");
        }

        ledgerEntry.setAmount(amount);
        ledgerEntry.setVnpayTxnRef(vnpayTxnRef);
    }

    private String normalizeTxnRef(String vnpayTxnRef) {
        if (vnpayTxnRef == null || vnpayTxnRef.isBlank()) {
            throw new IllegalArgumentException("vnpayTxnRef must not be blank");
        }
        String normalized = vnpayTxnRef.trim();
        if (normalized.length() > MAX_VNPAY_TXN_REF_LENGTH) {
            throw new IllegalArgumentException("vnpayTxnRef must not exceed 100 characters");
        }
        return normalized;
    }

    private PaymentProcessingResult alreadyProcessed(Transaction transaction) {
        return new PaymentProcessingResult(
                PaymentProcessingResult.Status.ALREADY_PROCESSED,
                transaction);
    }

    @FunctionalInterface
    public interface PaymentOperation {
        /**
         * Cap nhat vi/don trong DB va tao audit entry. Khong thuc hien external side effect.
         */
        Transaction apply();
    }
}
