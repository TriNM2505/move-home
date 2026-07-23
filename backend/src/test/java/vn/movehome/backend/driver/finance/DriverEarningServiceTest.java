package vn.movehome.backend.driver.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverEarningServiceTest {

    @Mock
    private DriverWalletRepository driverWalletRepository;

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private vn.movehome.backend.repository.NotificationRepository notificationRepository;

    private DriverEarningService service;

    @BeforeEach
    void setUp() {
        service = new DriverEarningService(
                driverWalletRepository,
                withdrawalRequestRepository,
                transactionRepository,
                userRepository,
                orderRepository,
                notificationRepository);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void creditEarningUpdatesWalletAndAppendsDriverAndPlatformTransactions() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("100000"))
                .totalEarned(new BigDecimal("200000"))
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH202606200001")
                .driverId(driverId)
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(false);
        when(userRepository.findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                UserRole.ADMIN,
                UserStatus.ACTIVE))
                .thenReturn(Optional.of(User.builder().id(adminId).build()));

        service.creditEarning(order);

        assertThat(wallet.getBalance()).isEqualByComparingTo("800000");
        assertThat(wallet.getTotalEarned()).isEqualByComparingTo("900000");
        verify(driverWalletRepository).save(wallet);

        ArgumentCaptor<Iterable<Transaction>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(transactionRepository).saveAll(captor.capture());
        List<Transaction> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .filteredOn(transaction -> transaction.getType() == TransactionType.DRIVER_EARNING)
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getUserId()).isEqualTo(driverId);
                    assertThat(transaction.getAmount()).isEqualByComparingTo("700000");
                    assertThat(transaction.getRelatedOrderId()).isEqualTo(orderId);
                });
        assertThat(saved)
                .filteredOn(transaction -> transaction.getType() == TransactionType.PLATFORM_FEE)
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getUserId()).isEqualTo(adminId);
                    assertThat(transaction.getAmount()).isEqualByComparingTo("300000");
                    assertThat(transaction.getRelatedOrderId()).isEqualTo(orderId);
                });
    }

    @Test
    void creditEarningSkipsWhenDriverEarningAlreadyExistsForOrder() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH202606200002")
                .driverId(driverId)
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(true);

        service.creditEarning(order);

        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getTotalEarned()).isEqualByComparingTo("0");
        verify(driverWalletRepository, never()).save(any());
        verify(transactionRepository, never()).saveAll(any());
        verify(userRepository, never()).findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                UserRole.ADMIN,
                UserStatus.ACTIVE);
    }

    @Test
    void createWithdrawalRejectsAmountGreaterThanAvailableBalance() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("1000000"))
                .totalEarned(new BigDecimal("1000000"))
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        WithdrawalRequest pending = WithdrawalRequest.builder()
                .driverId(driverId)
                .amount(new BigDecimal("500000"))
                .status("PENDING")
                .build();
        User driver = User.builder()
                .id(driverId)
                .fullName("Nguyễn Văn A")
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId))
                .thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.createWithdrawal(
                driver,
                new CreateWithdrawalRequest(new BigDecimal("600000"), "VCB", "0123456789")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).startsWith("INSUFFICIENT_AVAILABLE_BALANCE|");
                });

        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------
    // creditEarning — validation branches (validateCompletedOrder)
    // ---------------------------------------------------------------

    @Test
    void creditEarningRejectsNullOrder() {
        assertThatThrownBy(() -> service.creditEarning(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Đơn hàng không hợp lệ.");
                });
    }

    @Test
    void creditEarningRejectsOrderWithNullId() {
        ServiceOrder order = ServiceOrder.builder().id(null).status("COMPLETED").build();
        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Đơn hàng không hợp lệ."));
    }

    @Test
    void creditEarningRejectsOrderWithIneligibleStatus() {
        ServiceOrder order = ServiceOrder.builder()
                .id(UUID.randomUUID())
                .status("PENDING")
                .driverId(UUID.randomUUID())
                .totalQuote(new BigDecimal("1000000"))
                .build();

        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("INVALID_ORDER_STATUS|Chỉ ghi thu nhập cho đơn đã hoàn thành.");
                });
    }

    @Test
    void creditEarningRejectsOrderWithoutDriver() {
        ServiceOrder order = ServiceOrder.builder()
                .id(UUID.randomUUID())
                .status("COMPLETED")
                .driverId(null)
                .totalQuote(new BigDecimal("1000000"))
                .build();

        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("ORDER_DRIVER_REQUIRED|Đơn hàng chưa có tài xế nhận.");
                });
    }

    @Test
    void creditEarningRejectsOrderWithoutTotalQuote() {
        ServiceOrder order = ServiceOrder.builder()
                .id(UUID.randomUUID())
                .status("COMPLETED")
                .driverId(UUID.randomUUID())
                .totalQuote(null)
                .build();

        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Đơn hàng chưa có tổng tiền hợp lệ.");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "DISPUTED"})
    void creditEarningAcceptsBothEligibleStatuses(String status) {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH1")
                .driverId(driverId)
                .status(status)
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(false);
        when(userRepository.findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(Optional.of(User.builder().id(adminId).build()));

        service.creditEarning(order);

        assertThat(wallet.getBalance()).isEqualByComparingTo("700000");
        verify(driverWalletRepository).save(wallet);
    }

    @Test
    void creditEarningUsesDefaultCommissionRateWhenSnapshotIsNull() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH2")
                .driverId(driverId)
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(null)
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(false);
        when(userRepository.findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(Optional.of(User.builder().id(adminId).build()));

        service.creditEarning(order);

        // Mac dinh 30% commission -> con lai 70%
        assertThat(wallet.getBalance()).isEqualByComparingTo("700000");
    }

    @Test
    void creditEarningRejectsInvalidCommissionRateAndDoesNotMutateWalletTransactions() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("500000"))
                .totalEarned(new BigDecimal("500000"))
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH3")
                .driverId(driverId)
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("1.5"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("INVALID_COMMISSION_RATE|Tỷ lệ phí nền tảng của đơn hàng không hợp lệ.");
                });

        assertThat(wallet.getBalance()).isEqualByComparingTo("500000");
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void creditEarningThrowsWhenWalletMissing() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH4")
                .driverId(driverId)
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("DRIVER_WALLET_NOT_FOUND|Không tìm thấy ví tài xế.");
                });
    }

    @Test
    void creditEarningThrowsWhenPlatformAccountMissing() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH5")
                .driverId(driverId)
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(false);
        when(userRepository.findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creditEarning(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND|Không tìm thấy tài khoản nền tảng để ghi phí.");
                });
        verify(transactionRepository, never()).saveAll(any());
    }

    // ---------------------------------------------------------------
    // creditNoShowCompensation
    // ---------------------------------------------------------------

    @Test
    void creditNoShowCompensationCreditsDepositAndRecordsBalanceAfter() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("200000"))
                .totalEarned(new BigDecimal("200000"))
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH6")
                .driverId(driverId)
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(false);

        service.creditNoShowCompensation(order);

        // deposit = 1_000_000 * 0.3 = 300_000
        assertThat(wallet.getBalance()).isEqualByComparingTo("500000");
        assertThat(wallet.getTotalEarned()).isEqualByComparingTo("500000");
        verify(driverWalletRepository).save(wallet);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(driverId);
        assertThat(saved.getType()).isEqualTo(TransactionType.DRIVER_EARNING);
        assertThat(saved.getAmount()).isEqualByComparingTo("300000");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("500000");
        assertThat(saved.getRelatedOrderId()).isEqualTo(orderId);
        assertThat(saved.getDescription())
                .isEqualTo("Đền bù khách không có mặt tại điểm đón (giữ cọc) đơn " + order.getOrderCode());
    }

    @Test
    void creditNoShowCompensationSkipsWhenAlreadyCredited() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH7")
                .driverId(driverId)
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedOrderId(TransactionType.DRIVER_EARNING, orderId))
                .thenReturn(true);

        service.creditNoShowCompensation(order);

        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
        verify(driverWalletRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void creditNoShowCompensationRejectsNullOrder() {
        assertThatThrownBy(() -> service.creditNoShowCompensation(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Đơn hàng không hợp lệ."));
    }

    @Test
    void creditNoShowCompensationRejectsOrderWithoutDriver() {
        ServiceOrder order = ServiceOrder.builder()
                .id(UUID.randomUUID())
                .driverId(null)
                .totalQuote(new BigDecimal("1000000"))
                .build();

        assertThatThrownBy(() -> service.creditNoShowCompensation(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("ORDER_DRIVER_REQUIRED|Đơn hàng chưa có tài xế nhận.");
                });
    }

    @Test
    void creditNoShowCompensationRejectsOrderWithoutTotalQuote() {
        ServiceOrder order = ServiceOrder.builder()
                .id(UUID.randomUUID())
                .driverId(UUID.randomUUID())
                .totalQuote(null)
                .build();

        assertThatThrownBy(() -> service.creditNoShowCompensation(order))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Đơn hàng chưa có tổng tiền hợp lệ.");
                });
    }

    // ---------------------------------------------------------------
    // getWallet
    // ---------------------------------------------------------------

    @Test
    void getWalletReturnsBalanceSummary() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("100000"))
                .totalEarned(new BigDecimal("300000"))
                .totalWithdrawn(new BigDecimal("200000"))
                .build();
        when(driverWalletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        DriverWalletSummaryResponse response = service.getWallet(driverId);

        assertThat(response.balance()).isEqualByComparingTo("100000");
        assertThat(response.totalEarned()).isEqualByComparingTo("300000");
        assertThat(response.totalWithdrawn()).isEqualByComparingTo("200000");
        verify(driverWalletRepository).insertIfMissing(driverId);
    }

    @Test
    void getWalletDefaultsNullTotalsToZero() {
        // money(null) -> BigDecimal.ZERO.setScale(0): kiem tra nhanh gia tri null cho totalEarned/totalWithdrawn.
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("100000"))
                .totalEarned(null)
                .totalWithdrawn(null)
                .build();
        when(driverWalletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        DriverWalletSummaryResponse response = service.getWallet(driverId);

        assertThat(response.balance()).isEqualByComparingTo("100000");
        assertThat(response.totalEarned()).isEqualByComparingTo("0");
        assertThat(response.totalWithdrawn()).isEqualByComparingTo("0");
    }

    @Test
    void getWalletThrowsWhenWalletNotFound() {
        UUID driverId = UUID.randomUUID();
        when(driverWalletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getWallet(driverId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("DRIVER_WALLET_NOT_FOUND|Không tìm thấy ví tài xế.");
                });
    }

    // ---------------------------------------------------------------
    // getEarnings
    // ---------------------------------------------------------------

    @Test
    void getEarningsRejectsNegativePage() {
        assertThatThrownBy(() -> service.getEarnings(UUID.randomUUID(), -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số trang không hợp lệ.");
                });
    }

    @Test
    void getEarningsRejectsInvalidSize() {
        assertThatThrownBy(() -> service.getEarnings(UUID.randomUUID(), 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
                });
        assertThatThrownBy(() -> service.getEarnings(UUID.randomUUID(), 0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100."));
    }

    @Test
    void getEarningsMapsTransactionsAndResolvesOrderCodes() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Transaction withOrder = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(driverId)
                .type(TransactionType.DRIVER_EARNING)
                .amount(new BigDecimal("700000"))
                .relatedOrderId(orderId)
                .description("Thu nhập tài xế")
                .createdAt(Instant.parse("2026-06-20T07:00:00Z"))
                .build();
        Transaction withoutOrder = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(driverId)
                .type(TransactionType.WALLET_TOP_UP)
                .amount(new BigDecimal("50000"))
                .relatedOrderId(null)
                .description("Nộp bổ sung")
                .createdAt(Instant.parse("2026-06-21T07:00:00Z"))
                .build();
        Page<Transaction> page = new PageImpl<>(List.of(withOrder, withoutOrder));

        when(transactionRepository.findByUserIdAndTypeIn(eq(driverId), any(), any(PageRequest.class)))
                .thenReturn(page);
        ServiceOrder order = ServiceOrder.builder().id(orderId).orderCode("MH202606200099").build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(order));

        Page<DriverEarningResponse> result = service.getEarnings(driverId, 0, 20);

        assertThat(result.getContent()).hasSize(2);
        DriverEarningResponse first = result.getContent().get(0);
        assertThat(first.relatedOrderId()).isEqualTo(orderId);
        assertThat(first.orderCode()).isEqualTo("MH202606200099");
        assertThat(first.amount()).isEqualByComparingTo("700000");
        assertThat(first.createdAt()).isEqualTo(OffsetDateTime.parse("2026-06-20T07:00:00Z"));

        DriverEarningResponse second = result.getContent().get(1);
        assertThat(second.relatedOrderId()).isNull();
        assertThat(second.orderCode()).isNull();
    }

    @Test
    void getEarningsDuplicateOrderIdsFromRepository_mergeFunctionKeepsFirst() {
        // Collectors.toMap merge (left, right) -> left: chi kich hoat khi orderRepository.findAllById
        // tra ve 2 ban ghi trung id (truong hop du phong, khong xay ra trong flow that vi id la khoa chinh).
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Transaction withOrder = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(driverId)
                .type(TransactionType.DRIVER_EARNING)
                .amount(new BigDecimal("700000"))
                .relatedOrderId(orderId)
                .description("Thu nhập tài xế")
                .createdAt(Instant.parse("2026-06-20T07:00:00Z"))
                .build();
        Page<Transaction> page = new PageImpl<>(List.of(withOrder));
        when(transactionRepository.findByUserIdAndTypeIn(eq(driverId), any(), any(PageRequest.class)))
                .thenReturn(page);
        ServiceOrder order1 = ServiceOrder.builder().id(orderId).orderCode("MH202606200099").build();
        ServiceOrder order2Duplicate = ServiceOrder.builder().id(orderId).orderCode("MH202606200100").build();
        when(orderRepository.findAllById(any())).thenReturn(List.of(order1, order2Duplicate));

        Page<DriverEarningResponse> result = service.getEarnings(driverId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).orderCode()).isEqualTo("MH202606200099");
    }

    @Test
    void getEarningsSkipsOrderLookupWhenNoRelatedOrders() {
        UUID driverId = UUID.randomUUID();
        Transaction withoutOrder = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(driverId)
                .type(TransactionType.WALLET_TOP_UP)
                .amount(new BigDecimal("50000"))
                .relatedOrderId(null)
                .description("Nộp bổ sung")
                .createdAt(Instant.parse("2026-06-21T07:00:00Z"))
                .build();
        Page<Transaction> page = new PageImpl<>(List.of(withoutOrder));
        when(transactionRepository.findByUserIdAndTypeIn(eq(driverId), any(), any(PageRequest.class)))
                .thenReturn(page);

        service.getEarnings(driverId, 0, 20);

        verify(orderRepository, never()).findAllById(any());
    }

    // ---------------------------------------------------------------
    // createWithdrawal — validation branches
    // ---------------------------------------------------------------

    @Test
    void createWithdrawalRejectsNullRequest() {
        User driver = User.builder().id(UUID.randomUUID()).build();
        assertThatThrownBy(() -> service.createWithdrawal(driver, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập số tiền cần rút.");
                });
    }

    @Test
    void createWithdrawalRejectsNonIntegerAmount() {
        User driver = User.builder().id(UUID.randomUUID()).build();
        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("100000.50"), "VCB", "0123456789")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Số tiền rút phải là VND nguyên đồng.");
                });
    }

    @Test
    void createWithdrawalRejectsAmountBelowMinimum() {
        User driver = User.builder().id(UUID.randomUUID()).build();
        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("50000"), "VCB", "0123456789")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Số tiền rút tối thiểu là 100.000 VND.");
                });
    }

    @Test
    void createWithdrawalRejectsBlankBankCode() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("200000"), " ", "0123456789")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Vui lòng chọn ngân hàng nhận tiền.");
                });
    }

    @Test
    void createWithdrawalRejectsUnsupportedBankCode() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("200000"), "XYZ", "0123456789")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Ngân hàng không được hỗ trợ.");
                });
    }

    @Test
    void createWithdrawalRejectsBlankBankAccountNumber() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("200000"), "VCB", "  ")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Vui lòng nhập số tài khoản ngân hàng.");
                });
    }

    @Test
    void createWithdrawalRejectsInvalidBankAccountNumberFormat() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("200000"), "VCB", "abc123")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Số tài khoản không hợp lệ (phải gồm 8 đến 15 chữ số).");
                });
    }

    @Test
    void createWithdrawalClampsNegativeAvailableBalanceToZeroThenRejects() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        // Balance nho hon tong pending -> available am -> clamp ve 0
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("100000")).build();
        WithdrawalRequest pending = WithdrawalRequest.builder()
                .driverId(driverId).amount(new BigDecimal("500000")).status("PENDING").build();
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("100000"), "VCB", "0123456789")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).startsWith("INSUFFICIENT_AVAILABLE_BALANCE|"));
    }

    @Test
    void createWithdrawalHappyPathPersistsRequestAndNotifiesAdmins() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("  nguyễn   văn a  ").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        User admin = User.builder().id(adminId).build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponse response = service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("300000"), "vcb", "0123456789"));

        assertThat(response.amount()).isEqualByComparingTo("300000");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).isEqualTo("Yêu cầu rút tiền đã được gửi.");
        assertThat(response.requestedAt()).isNotNull();

        ArgumentCaptor<WithdrawalRequest> captor = ArgumentCaptor.forClass(WithdrawalRequest.class);
        verify(withdrawalRequestRepository).saveAndFlush(captor.capture());
        WithdrawalRequest saved = captor.getValue();
        assertThat(saved.getDriverId()).isEqualTo(driverId);
        assertThat(saved.getBankCode()).isEqualTo("VCB");
        assertThat(saved.getBankNameSnapshot()).isEqualTo("Vietcombank");
        assertThat(saved.getBankAccountNumber()).isEqualTo("0123456789");
        // normalizeAccountHolder: trim + collapse whitespace + uppercase (NFC normalize)
        assertThat(saved.getBankAccountHolder()).isEqualTo("NGUYỄN VĂN A");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getIdempotencyKey()).isNotNull();

        ArgumentCaptor<List<vn.movehome.backend.entity.Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notifCaptor.capture());
        List<vn.movehome.backend.entity.Notification> notifications = notifCaptor.getValue();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getUserId()).isEqualTo(adminId);
        assertThat(notifications.get(0).getType()).isEqualTo("WITHDRAWAL_REQUESTED");
        assertThat(notifications.get(0).getTitle()).isEqualTo("Yêu cầu rút tiền mới");
        // Thong bao dung driver.getFullName() nguyen ban (chua normalize) lam ten hien thi
        assertThat(notifications.get(0).getMessage())
                .isEqualTo("Tài xế   nguyễn   văn a   yêu cầu rút 300000 VND. Vui lòng xử lý.");
    }

    @Test
    void createWithdrawalUsesFallbackDriverNameWhenFullNameIsNull() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName(null).build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        User admin = User.builder().id(adminId).build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createWithdrawal(driver, new CreateWithdrawalRequest(new BigDecimal("300000"), "VCB", "0123456789"));

        ArgumentCaptor<WithdrawalRequest> captor = ArgumentCaptor.forClass(WithdrawalRequest.class);
        verify(withdrawalRequestRepository).saveAndFlush(captor.capture());
        // fullName null -> normalizeAccountHolder fallback "CHUA CAP NHAT"
        assertThat(captor.getValue().getBankAccountHolder()).isEqualTo("CHUA CAP NHAT");

        ArgumentCaptor<List<vn.movehome.backend.entity.Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notifCaptor.capture());
        assertThat(notifCaptor.getValue().get(0).getMessage())
                .isEqualTo("Tài xế Tài xế yêu cầu rút 300000 VND. Vui lòng xử lý.");
    }

    @Test
    void createWithdrawalTruncatesLongAccountHolderNameTo100Chars() {
        UUID driverId = UUID.randomUUID();
        String longName = "A".repeat(150);
        User driver = User.builder().id(driverId).fullName(longName).build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createWithdrawal(driver, new CreateWithdrawalRequest(new BigDecimal("300000"), "VCB", "0123456789"));

        ArgumentCaptor<WithdrawalRequest> captor = ArgumentCaptor.forClass(WithdrawalRequest.class);
        verify(withdrawalRequestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBankAccountHolder()).hasSize(100);
    }

    @Test
    void createWithdrawalSucceedsEvenWhenAdminNotificationLookupThrows() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenThrow(new RuntimeException("boom"));
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponse response = service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("300000"), "VCB", "0123456789"));

        assertThat(response.amount()).isEqualByComparingTo("300000");
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void createWithdrawalUsesExistingRequestedAtWhenAlreadyPersisted() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).fullName("Nguyễn Văn A").build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId).balance(new BigDecimal("1000000")).build();
        OffsetDateTime fixed = OffsetDateTime.parse("2026-05-01T10:00:00Z");

        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findPendingByDriverIdForUpdate(driverId)).thenReturn(List.of());
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> {
                    WithdrawalRequest arg = invocation.getArgument(0);
                    arg.setId(UUID.randomUUID());
                    arg.setRequestedAt(fixed);
                    return arg;
                });

        WithdrawalRequestResponse response = service.createWithdrawal(
                driver, new CreateWithdrawalRequest(new BigDecimal("300000"), "VCB", "0123456789"));

        assertThat(response.requestedAt()).isEqualTo(fixed);
    }

    // ---------------------------------------------------------------
    // getWithdrawals
    // ---------------------------------------------------------------

    @Test
    void getWithdrawalsRejectsInvalidPaging() {
        assertThatThrownBy(() -> service.getWithdrawals(UUID.randomUUID(), -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số trang không hợp lệ."));
    }

    @Test
    void getWithdrawalsMapsItemsAndMasksAccountNumbers() {
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest longAccount = WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .amount(new BigDecimal("300000"))
                .status("PROCESSED")
                .bankNameSnapshot("Vietcombank")
                .bankAccountNumber("0123456789")
                .requestedAt(OffsetDateTime.parse("2026-06-01T00:00:00Z"))
                .processedAt(OffsetDateTime.parse("2026-06-02T00:00:00Z"))
                .build();
        WithdrawalRequest shortAccount = WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .amount(new BigDecimal("150000"))
                .status("REJECTED")
                .bankNameSnapshot("BIDV")
                .bankAccountNumber("12")
                .rejectionReason("Sai thông tin")
                .requestedAt(OffsetDateTime.parse("2026-06-03T00:00:00Z"))
                .build();
        WithdrawalRequest nullAccount = WithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .amount(new BigDecimal("100000"))
                .status("PENDING")
                .bankAccountNumber(null)
                .requestedAt(OffsetDateTime.parse("2026-06-04T00:00:00Z"))
                .build();

        Page<WithdrawalRequest> page = new PageImpl<>(List.of(longAccount, shortAccount, nullAccount));
        when(withdrawalRequestRepository.findByDriverId(eq(driverId), any(PageRequest.class))).thenReturn(page);

        Page<DriverWithdrawalItemResponse> result = service.getWithdrawals(driverId, 0, 20);

        List<DriverWithdrawalItemResponse> items = result.getContent();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).bankAccountMasked()).isEqualTo("******6789");
        assertThat(items.get(1).bankAccountMasked()).isEqualTo("******12");
        assertThat(items.get(1).rejectionReason()).isEqualTo("Sai thông tin");
        assertThat(items.get(2).bankAccountMasked()).isNull();
    }

    @Test
    void defaultPageSizeReturnsConfiguredValue() {
        assertThat(service.defaultPageSize()).isEqualTo(20);
    }
}
