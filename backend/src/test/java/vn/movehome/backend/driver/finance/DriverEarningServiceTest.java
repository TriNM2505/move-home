package vn.movehome.backend.driver.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private DriverEarningService service;

    @BeforeEach
    void setUp() {
        service = new DriverEarningService(
                driverWalletRepository,
                withdrawalRequestRepository,
                transactionRepository,
                userRepository,
                orderRepository);
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
                new CreateWithdrawalRequest(new BigDecimal("600000"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).startsWith("INSUFFICIENT_AVAILABLE_BALANCE|");
                });

        verify(withdrawalRequestRepository, never()).saveAndFlush(any());
    }
}
