package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.finance.AdminWithdrawalItemResponse;
import vn.movehome.backend.dto.admin.finance.PendingWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
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
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    // ---------- getPending ----------

    @Test
    void getPendingRejectsNegativePage() {
        assertThatThrownBy(() -> service.getPending(-1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getPendingRejectsSizeZero() {
        assertThatThrownBy(() -> service.getPending(0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getPendingRejectsSizeAboveMax() {
        assertThatThrownBy(() -> service.getPending(0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void getPendingReturnsZeroOldestWaitingDaysWhenNoneFound() {
        when(withdrawalRequestRepository.findByStatus(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(withdrawalRequestRepository.findOldestPending(any(PageRequest.class))).thenReturn(List.of());
        when(withdrawalRequestRepository.countPending()).thenReturn(0L);
        when(withdrawalRequestRepository.sumPendingAmount()).thenReturn(BigDecimal.ZERO);
        when(withdrawalRequestRepository.countPendingRequestedBefore(any())).thenReturn(0L);

        PendingWithdrawalPageResponse response = service.getPending(0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.oldestWaitingDays()).isZero();
        assertThat(response.pendingCount()).isZero();
    }

    @Test
    void getPendingBuildsBlockingReasonsForEachDriverAndWalletState() {
        UUID driverIdMissing = UUID.randomUUID();
        UUID driverIdNotActive = UUID.randomUUID();
        UUID driverIdInsufficient = UUID.randomUUID();
        UUID driverIdBankMissing = UUID.randomUUID();
        UUID driverIdBankBlank = UUID.randomUUID();
        UUID driverIdClear = UUID.randomUUID();
        UUID driverIdNullAmount = UUID.randomUUID();
        UUID driverIdNullRequestedAt = UUID.randomUUID();

        WithdrawalRequest missingDriver = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdMissing).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest notActiveDriver = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdNotActive).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest insufficientBalance = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdInsufficient).amount(new BigDecimal("5000000"))
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest bankMissing = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdBankMissing).amount(new BigDecimal("100000"))
                .bankAccountNumber(null).requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest bankBlank = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdBankBlank).amount(new BigDecimal("100000"))
                .bankAccountNumber("   ").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest clear = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdClear).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest nullAmount = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdNullAmount).amount(null)
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        WithdrawalRequest nullRequestedAt = WithdrawalRequest.builder()
                .id(UUID.randomUUID()).driverId(driverIdNullRequestedAt).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234567890").requestedAt(null).status("PENDING")
                .build();

        List<WithdrawalRequest> withdrawals = List.of(missingDriver, notActiveDriver, insufficientBalance,
                bankMissing, bankBlank, clear, nullAmount, nullRequestedAt);

        when(withdrawalRequestRepository.findByStatus(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(withdrawals));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(
                User.builder().id(driverIdNotActive).fullName("Not Active").phone("0900000001")
                        .status(UserStatus.LOCKED).build(),
                User.builder().id(driverIdInsufficient).fullName("Insufficient").phone("0900000002")
                        .status(UserStatus.ACTIVE).build(),
                User.builder().id(driverIdBankMissing).fullName("Bank Missing").phone("0900000003")
                        .status(UserStatus.ACTIVE).build(),
                User.builder().id(driverIdBankBlank).fullName("Bank Blank").phone("0900000004")
                        .status(UserStatus.ACTIVE).build(),
                User.builder().id(driverIdClear).fullName("Clear").phone("0900000005")
                        .status(UserStatus.ACTIVE).build(),
                User.builder().id(driverIdNullAmount).fullName("Null Amount").phone("0900000006")
                        .status(UserStatus.ACTIVE).build(),
                User.builder().id(driverIdNullRequestedAt).fullName("Null Requested").phone("0900000007")
                        .status(UserStatus.ACTIVE).build()
        ));
        when(driverWalletRepository.findByDriverIdIn(any())).thenReturn(List.of(
                DriverWallet.builder().driverId(driverIdMissing).balance(new BigDecimal("1000000")).build(),
                DriverWallet.builder().driverId(driverIdNotActive).balance(new BigDecimal("1000000")).build(),
                DriverWallet.builder().driverId(driverIdInsufficient).balance(new BigDecimal("100")).build(),
                DriverWallet.builder().driverId(driverIdBankMissing).balance(new BigDecimal("1000000")).build(),
                DriverWallet.builder().driverId(driverIdBankBlank).balance(new BigDecimal("1000000")).build(),
                DriverWallet.builder().driverId(driverIdClear).balance(new BigDecimal("1000000")).build(),
                DriverWallet.builder().driverId(driverIdNullRequestedAt).balance(new BigDecimal("1000000")).build()
                // driverIdNullAmount intentionally has no wallet entry -> wallet == null branch
        ));
        when(withdrawalRequestRepository.findOldestPending(any(PageRequest.class)))
                .thenReturn(List.of(clear));
        when(withdrawalRequestRepository.countPending()).thenReturn(8L);
        when(withdrawalRequestRepository.sumPendingAmount()).thenReturn(new BigDecimal("6400100"));
        when(withdrawalRequestRepository.countPendingRequestedBefore(any())).thenReturn(1L);

        PendingWithdrawalPageResponse response = service.getPending(0, 20);

        List<AdminWithdrawalItemResponse> content = response.content();
        assertThat(content).hasSize(8);

        AdminWithdrawalItemResponse missingItem = findByDriverId(content, driverIdMissing);
        assertThat(missingItem.blockingReasons()).containsExactly("DRIVER_NOT_FOUND");
        assertThat(missingItem.processReady()).isFalse();
        assertThat(missingItem.bankAccountMasked()).isEqualTo("******7890");

        AdminWithdrawalItemResponse notActiveItem = findByDriverId(content, driverIdNotActive);
        assertThat(notActiveItem.blockingReasons()).containsExactly("DRIVER_NOT_ACTIVE");

        AdminWithdrawalItemResponse insufficientItem = findByDriverId(content, driverIdInsufficient);
        assertThat(insufficientItem.blockingReasons()).containsExactly("INSUFFICIENT_CURRENT_BALANCE");

        AdminWithdrawalItemResponse bankMissingItem = findByDriverId(content, driverIdBankMissing);
        assertThat(bankMissingItem.blockingReasons()).containsExactly("BANK_ACCOUNT_MISSING");
        assertThat(bankMissingItem.bankAccountMasked()).isNull();

        AdminWithdrawalItemResponse bankBlankItem = findByDriverId(content, driverIdBankBlank);
        assertThat(bankBlankItem.blockingReasons()).containsExactly("BANK_ACCOUNT_MISSING");

        AdminWithdrawalItemResponse clearItem = findByDriverId(content, driverIdClear);
        assertThat(clearItem.blockingReasons()).isEmpty();
        assertThat(clearItem.processReady()).isTrue();
        assertThat(clearItem.bankAccountMasked()).isEqualTo("******1234");
        assertThat(clearItem.daysWaiting()).isZero();

        AdminWithdrawalItemResponse nullAmountItem = findByDriverId(content, driverIdNullAmount);
        assertThat(nullAmountItem.amount()).isEqualByComparingTo("0");
        assertThat(nullAmountItem.walletBalance()).isEqualByComparingTo("0");
        assertThat(nullAmountItem.blockingReasons()).isEmpty();

        AdminWithdrawalItemResponse nullRequestedAtItem = findByDriverId(content, driverIdNullRequestedAt);
        assertThat(nullRequestedAtItem.daysWaiting()).isZero();

        assertThat(response.oldestWaitingDays()).isZero();
        assertThat(response.pendingCount()).isEqualTo(8L);
        assertThat(response.overSlaCount()).isEqualTo(1L);
    }

    private AdminWithdrawalItemResponse findByDriverId(List<AdminWithdrawalItemResponse> content, UUID driverId) {
        return content.stream().filter(item -> item.driverId().equals(driverId)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing item for driver " + driverId));
    }

    // ---------- process ----------

    @Test
    void processThrowsNotFoundWhenWithdrawalMissing() {
        UUID withdrawalId = UUID.randomUUID();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("VALID-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void processThrowsWhenBankTxnRefBlank() {
        assertThatThrownBy(() -> service.process(UUID.randomUUID(), admin(),
                new ProcessWithdrawalRequest("   ", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processThrowsWhenBankTxnRefTooShort() {
        assertThatThrownBy(() -> service.process(UUID.randomUUID(), admin(),
                new ProcessWithdrawalRequest("A1", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processThrowsWhenBankTxnRefTooLong() {
        String tooLong = "A".repeat(101);
        assertThatThrownBy(() -> service.process(UUID.randomUUID(), admin(),
                new ProcessWithdrawalRequest(tooLong, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processThrowsWhenBankTxnRefHasInvalidCharacters() {
        assertThatThrownBy(() -> service.process(UUID.randomUUID(), admin(),
                new ProcessWithdrawalRequest("VALID REF!!", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processThrowsWhenProcessingNoteTooLong() {
        String longNote = "a".repeat(501);
        assertThatThrownBy(() -> service.process(UUID.randomUUID(), admin(),
                new ProcessWithdrawalRequest("VALID-REF-01", longNote)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processReplaysWhenAlreadyProcessedWithSameBankTxnRef() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").bankTxnRef("VCB-EXISTING").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(transactionRepository.findByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(Optional.of(Transaction.builder().balanceAfter(new BigDecimal("2500000")).build()));

        WithdrawalActionResponse response = service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("VCB-EXISTING", null));

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(response.balanceAfter()).isEqualByComparingTo("2500000");
        verify(driverWalletRepository, never()).findByDriverIdForUpdate(any());
    }

    @Test
    void processReplayReturnsNullBalanceWhenNoMatchingTransactionFound() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").bankTxnRef("VCB-EXISTING").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(transactionRepository.findByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(Optional.empty());

        WithdrawalActionResponse response = service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("VCB-EXISTING", null));

        assertThat(response.balanceAfter()).isNull();
    }

    @Test
    void processThrowsConflictWhenAlreadyProcessedWithDifferentBankTxnRef() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").bankTxnRef("OLD-REF").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictWhenAdminIsTheDriverOfTheWithdrawal() {
        UUID withdrawalId = UUID.randomUUID();
        User admin = admin();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(admin.getId()).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.process(withdrawalId, admin,
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictWhenBankTxnRefAlreadyUsed() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(true);

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictWhenWalletNotFound() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsUnprocessableWhenBalanceInsufficient() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("100")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processThrowsConflictWhenWithdrawalTransactionAlreadyExists() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictOnDataIntegrityViolationDuringSave() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("dup")).when(transactionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processSetsProcessedByNullWhenAdminIdIsNull() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User adminWithoutId = User.builder().email("noid@movehome.vn").role(UserRole.ADMIN).build();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        service.process(withdrawalId, adminWithoutId, new ProcessWithdrawalRequest("NEW-REF-01", null));

        assertThat(withdrawal.getProcessedBy()).isNull();
        assertThat(withdrawal.getStatus()).isEqualTo("PROCESSED");
    }

    @Test
    void processThrowsNpeWhenAdminIsNull() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.process(withdrawalId, null,
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- reject ----------

    @Test
    void rejectThrowsWhenReasonBlank() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), admin(), new RejectWithdrawalRequest("   ")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void rejectThrowsWhenReasonTooShort() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), admin(), new RejectWithdrawalRequest("short")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void rejectThrowsWhenReasonHasNoLetters() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), admin(), new RejectWithdrawalRequest("1234567890123")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void rejectThrowsWhenReasonTooLong() {
        String longReason = "a".repeat(501);
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), admin(), new RejectWithdrawalRequest(longReason)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void rejectThrowsNotFoundWhenWithdrawalMissing() {
        UUID withdrawalId = UUID.randomUUID();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Ly do khong hop le")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectReplaysWhenAlreadyRejectedWithSameReason() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("REJECTED").rejectionReason("Ly do trung khop").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        WithdrawalActionResponse response = service.reject(withdrawalId, admin(),
                new RejectWithdrawalRequest("Ly do trung khop"));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(auditLogRepository, never()).saveAndFlush(any(AuditLog.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void rejectThrowsConflictWhenNotPending() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Ly do khac")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void defaultPageSizeReturnsTwenty() {
        assertThat(service.defaultPageSize()).isEqualTo(20);
    }

    @Test
    void processThrowsWhenRequestIsNull() {
        assertThatThrownBy(() -> service.process(UUID.randomUUID(), admin(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_BANK_TXN_REF|");
                });
    }

    @Test
    void rejectThrowsWhenRequestIsNull() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), admin(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_REJECTION_REASON|");
                });
    }

    @Test
    void rejectThrowsConflictWhenAlreadyRejectedWithDifferentReason() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("REJECTED").rejectionReason("Ly do cu").build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Ly do moi khac")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processTreatsBlankProcessingNoteAsAbsentAndWritesAuditWithoutNote() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        service.process(withdrawalId, admin(), new ProcessWithdrawalRequest("NEW-REF-01", "   "));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDetail()).contains("note=none");
    }

    @Test
    void processWritesAuditWithNotePresentWhenProcessingNoteProvided() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .id(withdrawalId).driverId(driverId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();
        when(withdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(withdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        service.process(withdrawalId, admin(), new ProcessWithdrawalRequest("NEW-REF-01", "Ghi chu hop le"));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDetail()).contains("note=present");
    }

    @Test
    void loadUsersReturnsEmptyMapWhenIdsCollectionIsNullViaDirectInvocation() {
        // loadUsers luon duoc goi voi mot Set tu stream() nen nhanh ids == null la defensive code
        // khong the tiep can qua public API. Kiem tra truc tiep bang reflection.
        Map<?, ?> result = ReflectionTestUtils.invokeMethod(service, "loadUsers", (Object) null);

        assertThat(result).isEmpty();
    }

    @Test
    void loadWalletsReturnsEmptyMapWhenDriverIdsSetIsNullViaDirectInvocation() {
        Map<?, ?> result = ReflectionTestUtils.invokeMethod(service, "loadWallets", (Object) null);

        assertThat(result).isEmpty();
    }

    @Test
    void daysBetweenReturnsZeroWhenToIsNullViaDirectInvocation() {
        // daysBetween luon duoc goi voi "to" la OffsetDateTime.now(...) (khong bao gio null) tu ca hai
        // vi tri goi trong service. Nhanh to == null la defensive code, kiem tra truc tiep bang reflection.
        Long result = ReflectionTestUtils.invokeMethod(service, "daysBetween",
                OffsetDateTime.now(ZoneOffset.UTC), null);

        assertThat(result).isZero();
    }

    @Test
    void writeAuditHandlesNullAdminViaDirectInvocation() {
        // writeAudit chi duoc goi sau khi admin.getId() da duoc truy cap thanh cong (NPE se xay ra truoc
        // neu admin null), nen nhanh admin == null la defensive code khong the tiep can qua public API.
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().id(withdrawalId).build();

        ReflectionTestUtils.invokeMethod(service, "writeAudit", null, "TEST_ACTION", withdrawal, "detail");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActorId()).isNull();
        assertThat(auditCaptor.getValue().getActorEmail()).isNull();
    }
}
