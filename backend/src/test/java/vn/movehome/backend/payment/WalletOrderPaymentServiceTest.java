package vn.movehome.backend.payment;

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
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletOrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OrderStatusTransitionService orderStatusTransitionService;

    private WalletOrderPaymentService service() {
        return new WalletOrderPaymentService(
                orderRepository, walletRepository, transactionRepository, orderStatusTransitionService);
    }

    private ServiceOrder order(UUID orderId, UUID customerId, String total) {
        return ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH202607090001")
                .status("PENDING_PAYMENT")
                .totalQuote(new BigDecimal(total))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
    }

    @Test
    void paysDepositFromWalletDeductsBalanceRecordsLedgerAndConfirms() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, "1000000"); // coc 30% = 300.000
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("500000"))
                .totalToppedUp(new BigDecimal("500000"))
                .totalSpent(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

        service().payDepositFromWallet(customerId, orderId);

        // Vi bi tru dung 300.000 (HR-18: khong am)
        assertThat(wallet.getBalance()).isEqualByComparingTo("200000");
        assertThat(wallet.getTotalSpent()).isEqualByComparingTo("300000");

        // Ghi 1 transaction am + balance_after (AC-13)
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction ledger = captor.getValue();
        assertThat(ledger.getType()).isEqualTo(TransactionType.ORDER_PAYMENT);
        assertThat(ledger.getAmount()).isEqualByComparingTo("-300000");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("200000");
        assertThat(ledger.getRelatedOrderId()).isEqualTo(orderId);

        // Don ve CONFIRMED qua transition service (actor CUSTOMER)
        verify(orderStatusTransitionService).transition(
                eq(order), eq("CONFIRMED"), eq(customerId), eq("CUSTOMER"), any());
    }

    @Test
    void insufficientBalanceRejectsWithoutMutating() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, "1000000"); // coc 30% = 300.000
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("100000")) // khong du
                .totalToppedUp(new BigDecimal("100000"))
                .totalSpent(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service().payDepositFromWallet(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(wallet.getBalance()).isEqualByComparingTo("100000"); // khong doi
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(orderStatusTransitionService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    void paysFinalFromWalletDeductsRemainingAndSetsFinalPaidWithoutChangingStatus() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .orderCode("MH202607090002")
                .status("AWAITING_FINAL_PAYMENT")
                .totalQuote(new BigDecimal("1000000")) // con lai 70% = 700.000
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("1000000"))
                .totalToppedUp(new BigDecimal("1000000"))
                .totalSpent(BigDecimal.ZERO)
                .build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

        service().payFinalFromWallet(customerId, orderId);

        assertThat(wallet.getBalance()).isEqualByComparingTo("300000"); // 1.000.000 - 700.000
        assertThat(order.getFinalPaidAt()).isNotNull();

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-700000");
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("300000");

        // Tra not KHONG doi status (van AWAITING_FINAL_PAYMENT cho tai xe hoan thanh)
        verify(orderStatusTransitionService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWhenOrderBelongsToAnotherCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomer = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = order(orderId, otherCustomer, "1000000");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().payDepositFromWallet(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(walletRepository, never()).findByCustomerIdForUpdate(any());
        verify(orderStatusTransitionService, never()).transition(any(), any(), any(), any(), any());
    }
}
