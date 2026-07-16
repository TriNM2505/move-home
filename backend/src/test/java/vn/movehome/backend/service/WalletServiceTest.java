package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestRepository;
import vn.movehome.backend.dto.customer.wallet.TransactionDTO;
import vn.movehome.backend.dto.customer.wallet.WalletSummaryDTO;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.WalletTransaction;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private CustomerWithdrawalRequestRepository customerWithdrawalRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository,
                customerWithdrawalRequestRepository, userRepository, notificationRepository);
    }

    @Test
    void getOrCreateSummaryReturnsExistingWallet() {
        UUID customerId = UUID.randomUUID();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("500000"))
                .totalToppedUp(new BigDecimal("1000000"))
                .totalSpent(new BigDecimal("500000"))
                .build();
        when(walletRepository.findByCustomerId(customerId)).thenReturn(Optional.of(wallet));

        WalletSummaryDTO result = walletService.getOrCreateSummary(customerId);

        assertThat(result.balance()).isEqualByComparingTo("500000");
        assertThat(result.totalToppedUp()).isEqualByComparingTo("1000000");
        assertThat(result.totalSpent()).isEqualByComparingTo("500000");
    }

    @Test
    void getOrCreateSummaryCreatesNewWalletWhenNotFound() {
        UUID customerId = UUID.randomUUID();
        CustomerWallet newWallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(BigDecimal.ZERO)
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .build();
        when(walletRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(CustomerWallet.class))).thenReturn(newWallet);

        WalletSummaryDTO result = walletService.getOrCreateSummary(customerId);

        assertThat(result.balance()).isEqualByComparingTo("0");
        verify(walletRepository).save(any(CustomerWallet.class));
    }

    @Test
    void getTransactionsRejectsNegativePage() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> walletService.getTransactions(customerId, -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getTransactionsRejectsZeroSize() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> walletService.getTransactions(customerId, 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getTransactionsRejectsSizeAbove100() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> walletService.getTransactions(customerId, 0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getTransactionsReturnsMappedPageWithMaskedVnpayRef() {
        UUID customerId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        WalletTransaction tx = new WalletTransaction(
                txId, customerId, "DEPOSIT",
                new BigDecimal("200000"), null, "Nap tien", "1234567890ABCD12", null);
        tx.setCreatedAt(OffsetDateTime.now());
        Page<WalletTransaction> page = new PageImpl<>(List.of(tx));
        when(transactionRepository.findByUserId(any(UUID.class), any(Pageable.class))).thenReturn(page);

        Page<TransactionDTO> result = walletService.getTransactions(customerId, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).vnpayTxnRefMasked()).isEqualTo("****CD12");
    }

    @Test
    void getTransactionsNullVnpayRefMapsToNull() {
        UUID customerId = UUID.randomUUID();
        WalletTransaction tx = new WalletTransaction(
                UUID.randomUUID(), customerId, "DRIVER_EARNING",
                new BigDecimal("700000"), UUID.randomUUID(), "Thu nhap", null, null);
        tx.setCreatedAt(OffsetDateTime.now());
        when(transactionRepository.findByUserId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        Page<TransactionDTO> result = walletService.getTransactions(customerId, 0, 20);

        assertThat(result.getContent().get(0).vnpayTxnRefMasked()).isNull();
    }

    @Test
    void defaultPageSizeIsCorrect() {
        assertThat(walletService.defaultPageSize()).isEqualTo(20);
    }

    /**
     * maskVnpayTxnRef: khi do dai chuoi <= 4, tra ve "****" thay vi cat phan cuoi.
     * Vi du: "12" → "****" (khong phai "****12").
     */
    @Test
    void getTransactionsShortVnpayRefReturnsAllMasked() {
        UUID customerId = UUID.randomUUID();
        // Ref chi co 4 ky tu — nut boundary: trimmed.length() <= 4 → "****"
        WalletTransaction tx = new WalletTransaction(
                UUID.randomUUID(), customerId, "DEPOSIT",
                new BigDecimal("100000"), null, "Nap tien ngan", "1234", null);
        tx.setCreatedAt(OffsetDateTime.now());
        when(transactionRepository.findByUserId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        Page<TransactionDTO> result = walletService.getTransactions(customerId, 0, 20);

        // "1234" co 4 ky tu → tra ve "****" (khong hien thi ky tu nao)
        assertThat(result.getContent().get(0).vnpayTxnRefMasked()).isEqualTo("****");
    }

    /**
     * maskVnpayTxnRef: khi ref la blank (chi khoang trang), tra ve null.
     */
    @Test
    void getTransactionsBlankVnpayRefMapsToNull() {
        UUID customerId = UUID.randomUUID();
        WalletTransaction tx = new WalletTransaction(
                UUID.randomUUID(), customerId, "DEPOSIT",
                new BigDecimal("50000"), null, "Test", "   ", null);
        tx.setCreatedAt(OffsetDateTime.now());
        when(transactionRepository.findByUserId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        Page<TransactionDTO> result = walletService.getTransactions(customerId, 0, 20);

        assertThat(result.getContent().get(0).vnpayTxnRefMasked()).isNull();
    }
}
