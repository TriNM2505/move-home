package vn.movehome.backend.dispute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRefundServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private CustomerRefundService service;

    @BeforeEach
    void setUp() {
        service = new CustomerRefundService(walletRepository, transactionRepository);
    }

    @Test
    void refundCreditsCustomerWalletAndAppendsRefundTransaction() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("300000"))
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .build();

        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

        BigDecimal balanceAfter = service.refundForDispute(
                customerId,
                orderId,
                disputeId,
                new BigDecimal("200000"),
                "Hoan tien khieu nai");

        assertThat(balanceAfter).isEqualByComparingTo("500000");
        assertThat(wallet.getBalance()).isEqualByComparingTo("500000");
        verify(walletRepository).insertIfMissing(customerId);
        verify(walletRepository).saveAndFlush(wallet);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getUserId()).isEqualTo(customerId);
        assertThat(transaction.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(transaction.getAmount()).isEqualByComparingTo("200000");
        assertThat(transaction.getRelatedOrderId()).isEqualTo(orderId);
        assertThat(transaction.getRelatedDisputeId()).isEqualTo(disputeId);
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("500000");
    }

    @Test
    void refundForCancellationCreditsWalletAndAppendsRefundTransactionWithoutDisputeId() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("100000"))
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .build();

        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

        BigDecimal balanceAfter = service.refundForCancellation(
                customerId,
                orderId,
                new BigDecimal("50000"),
                "Hoan coc huy don");

        assertThat(balanceAfter).isEqualByComparingTo("150000");
        assertThat(wallet.getBalance()).isEqualByComparingTo("150000");
        verify(walletRepository).insertIfMissing(customerId);
        verify(walletRepository).saveAndFlush(wallet);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getUserId()).isEqualTo(customerId);
        assertThat(transaction.getType()).isEqualTo(TransactionType.REFUND);
        assertThat(transaction.getAmount()).isEqualByComparingTo("50000");
        assertThat(transaction.getRelatedOrderId()).isEqualTo(orderId);
        assertThat(transaction.getRelatedDisputeId()).isNull();
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("150000");
    }

    @Test
    void refundForDisputeThrowsConflictWhenCustomerWalletIsMissing() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refundForDispute(
                customerId, orderId, disputeId, new BigDecimal("100000"), "Hoan tien"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("CUSTOMER_WALLET_NOT_FOUND|Khong tim thay vi khach hang.");
                });

        verify(walletRepository).insertIfMissing(customerId);
        verify(walletRepository, never()).saveAndFlush(any(CustomerWallet.class));
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void refundForCancellationThrowsConflictWhenCustomerWalletIsMissing() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refundForCancellation(
                customerId, orderId, new BigDecimal("100000"), "Hoan coc"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("CUSTOMER_WALLET_NOT_FOUND|Khong tim thay vi khach hang.");
                });

        verify(walletRepository, never()).saveAndFlush(any(CustomerWallet.class));
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void refundForDisputeRejectsNullAmountAsUnprocessable() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.refundForDispute(customerId, orderId, disputeId, null, "Hoan tien"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_RESOLUTION_AMOUNT|So tien xu ly khong hop le.");
                });

        verify(walletRepository, never()).insertIfMissing(any());
        verify(walletRepository, never()).findByCustomerIdForUpdate(any());
    }

    @Test
    void refundForDisputeRejectsNonIntegerVndAmountAsUnprocessable() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.refundForDispute(
                customerId, orderId, disputeId, new BigDecimal("100000.55"), "Hoan tien"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_RESOLUTION_AMOUNT|So tien xu ly phai la VND nguyen dong.");
                });

        verify(walletRepository, never()).insertIfMissing(any());
    }
}
