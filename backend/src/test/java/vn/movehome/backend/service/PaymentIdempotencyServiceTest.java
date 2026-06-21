package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyServiceTest {

    private static final String VNPAY_TXN_REF = "VNPAY-20260621-0001";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PaymentIdempotencyService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new PaymentIdempotencyService(transactionRepository, transactionManager);
    }

    @Test
    void sameVnpayTxnRefTwiceAppendsOnceAndDoesNotChangeBalanceAgain() {
        UUID customerId = UUID.randomUUID();
        AtomicReference<BigDecimal> balance = new AtomicReference<>(new BigDecimal("100000"));
        AtomicReference<Transaction> persisted = new AtomicReference<>();
        AtomicInteger operationCalls = new AtomicInteger();

        when(transactionRepository.findByVnpayTxnRef(VNPAY_TXN_REF))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setId(UUID.randomUUID());
                    persisted.set(transaction);
                    return transaction;
                });

        PaymentIdempotencyService.PaymentOperation paymentOperation = () -> {
            operationCalls.incrementAndGet();
            balance.updateAndGet(current -> current.add(new BigDecimal("500000")));
            return Transaction.builder()
                    .userId(customerId)
                    .type(TransactionType.ORDER_PAYMENT)
                    .amount(new BigDecimal("500000"))
                    .description("Thanh toan don hang qua VNPay")
                    .build();
        };

        PaymentProcessingResult first = service.process(VNPAY_TXN_REF, paymentOperation);
        BigDecimal balanceAfterFirstIpn = balance.get();
        PaymentProcessingResult duplicate = service.process(VNPAY_TXN_REF, paymentOperation);

        assertThat(first.status()).isEqualTo(PaymentProcessingResult.Status.PROCESSED);
        assertThat(duplicate.status()).isEqualTo(PaymentProcessingResult.Status.ALREADY_PROCESSED);
        assertThat(duplicate.transaction()).isSameAs(first.transaction());
        assertThat(operationCalls).hasValue(1);
        assertThat(balanceAfterFirstIpn).isEqualByComparingTo("600000");
        assertThat(balance.get()).isEqualByComparingTo(balanceAfterFirstIpn);
        assertThat(persisted.get().getVnpayTxnRef()).isEqualTo(VNPAY_TXN_REF);
        assertThat(persisted.get().getAmount()).hasScaleOf(0);
        verify(transactionRepository).saveAndFlush(any(Transaction.class));
        verify(transactionManager).commit(any());
    }

    @Test
    void uniqueConstraintRaceReturnsWinningTransaction() {
        Transaction winningTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(TransactionType.ORDER_PAYMENT)
                .amount(new BigDecimal("500000"))
                .vnpayTxnRef(VNPAY_TXN_REF)
                .build();
        AtomicInteger operationCalls = new AtomicInteger();
        AtomicInteger lookupCalls = new AtomicInteger();

        when(transactionRepository.findByVnpayTxnRef(VNPAY_TXN_REF))
                .thenAnswer(invocation -> lookupCalls.incrementAndGet() <= 2
                        ? Optional.empty()
                        : Optional.of(winningTransaction));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate vnpay_txn_ref"));

        PaymentProcessingResult result = service.process(VNPAY_TXN_REF, () -> {
            operationCalls.incrementAndGet();
            return Transaction.builder()
                    .userId(UUID.randomUUID())
                    .type(TransactionType.ORDER_PAYMENT)
                    .amount(new BigDecimal("500000"))
                    .build();
        });

        assertThat(result.status()).isEqualTo(PaymentProcessingResult.Status.ALREADY_PROCESSED);
        assertThat(result.transaction()).isSameAs(winningTransaction);
        assertThat(operationCalls).hasValue(1);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void fractionalVndAmountIsRejectedAndRolledBack() {
        when(transactionRepository.findByVnpayTxnRef(VNPAY_TXN_REF)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(VNPAY_TXN_REF, () -> Transaction.builder()
                .userId(UUID.randomUUID())
                .type(TransactionType.ORDER_PAYMENT)
                .amount(new BigDecimal("1000.5"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be an integer VND value");

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
        verify(transactionManager).rollback(any());
    }
}
