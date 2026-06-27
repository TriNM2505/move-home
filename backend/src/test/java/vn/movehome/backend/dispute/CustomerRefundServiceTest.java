package vn.movehome.backend.dispute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
