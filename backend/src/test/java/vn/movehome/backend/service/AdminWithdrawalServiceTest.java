package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.driver.finance.WithdrawalRequest;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWithdrawalServiceTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private DriverWalletRepository driverWalletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private AdminWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new AdminWithdrawalService(
                withdrawalRequestRepository,
                driverWalletRepository,
                transactionRepository,
                userRepository,
                auditLogRepository,
                notificationRepository);
    }

    @Test
    void processDebitsWalletAndAppendsWithdrawalTransactionAndAudit() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User admin = admin();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId)
                .driverId(driverId)
                .amount(new BigDecimal("1000000"))
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .bankAccountNumber("1234567890")
                .bankAccountHolder("NGUYEN VAN A")
                .status("PENDING")
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("3000000"))
                .totalWithdrawn(new BigDecimal("500000"))
                .build();

        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("VCB-20260609-001")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        service.process(withdrawalId, admin, new ProcessWithdrawalRequest("VCB-20260609-001", null));

        assertThat(wallet.getBalance()).isEqualByComparingTo("2000000");
        assertThat(wallet.getTotalWithdrawn()).isEqualByComparingTo("1500000");
        assertThat(withdrawal.getStatus()).isEqualTo("PROCESSED");
        assertThat(withdrawal.getProcessedBy()).isEqualTo(admin.getId());
        assertThat(withdrawal.getBankTxnRef()).isEqualTo("VCB-20260609-001");

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("-1000000");
        assertThat(transactionCaptor.getValue().getRelatedWithdrawalId()).isEqualTo(withdrawalId);
        assertThat(transactionCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("2000000");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("WITHDRAWAL_PROCESSED");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void rejectDoesNotMutateWalletOrAppendMoneyTransaction() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId)
                .driverId(UUID.randomUUID())
                .amount(new BigDecimal("700000"))
                .status("PENDING")
                .build();

        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Thong tin ngan hang khong hop le"));

        assertThat(withdrawal.getStatus()).isEqualTo("REJECTED");
        assertThat(withdrawal.getRejectionReason()).isEqualTo("Thong tin ngan hang khong hop le");
        verify(driverWalletRepository, never()).findByDriverIdForUpdate(any());
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
        verify(auditLogRepository).saveAndFlush(any(AuditLog.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    private User admin() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("admin@movehome.vn")
                .role(UserRole.ADMIN)
                .build();
    }
}
