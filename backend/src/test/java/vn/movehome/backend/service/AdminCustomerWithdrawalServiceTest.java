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
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestRepository;
import vn.movehome.backend.dto.admin.finance.AdminCustomerWithdrawalItemResponse;
import vn.movehome.backend.dto.admin.finance.PendingCustomerWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
import vn.movehome.backend.entity.AuditLog;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;

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
class AdminCustomerWithdrawalServiceTest {

    @Mock
    private CustomerWithdrawalRequestRepository customerWithdrawalRequestRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private AdminCustomerWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new AdminCustomerWithdrawalService(
                customerWithdrawalRequestRepository,
                walletRepository,
                transactionRepository,
                userRepository,
                auditLogRepository,
                notificationRepository);
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
        when(customerWithdrawalRequestRepository.findByStatus(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(customerWithdrawalRequestRepository.findOldestPending(any(PageRequest.class))).thenReturn(List.of());
        when(customerWithdrawalRequestRepository.countPending()).thenReturn(0L);
        when(customerWithdrawalRequestRepository.sumPendingAmount()).thenReturn(BigDecimal.ZERO);
        when(customerWithdrawalRequestRepository.countPendingRequestedBefore(any())).thenReturn(0L);

        PendingCustomerWithdrawalPageResponse response = service.getPending(0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.oldestWaitingDays()).isZero();
        assertThat(response.pendingCount()).isZero();
    }

    @Test
    void getPendingBuildsBlockingReasonsForEachCustomerAndWalletState() {
        UUID customerIdMissing = UUID.randomUUID();
        UUID customerIdInsufficient = UUID.randomUUID();
        UUID customerIdBankMissing = UUID.randomUUID();
        UUID customerIdBankBlank = UUID.randomUUID();
        UUID customerIdClear = UUID.randomUUID();
        UUID customerIdNullAmount = UUID.randomUUID();
        UUID customerIdNullRequestedAt = UUID.randomUUID();

        CustomerWithdrawalRequest missingCustomer = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdMissing).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        CustomerWithdrawalRequest insufficientBalance = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdInsufficient).amount(new BigDecimal("5000000"))
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        CustomerWithdrawalRequest bankMissing = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdBankMissing).amount(new BigDecimal("100000"))
                .bankAccountNumber(null).requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        CustomerWithdrawalRequest bankBlank = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdBankBlank).amount(new BigDecimal("100000"))
                .bankAccountNumber("   ").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        CustomerWithdrawalRequest clear = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdClear).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        CustomerWithdrawalRequest nullAmount = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdNullAmount).amount(null)
                .bankAccountNumber("1234567890").requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).status("PENDING")
                .build();
        CustomerWithdrawalRequest nullRequestedAt = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID()).customerId(customerIdNullRequestedAt).amount(new BigDecimal("100000"))
                .bankAccountNumber("1234567890").requestedAt(null).status("PENDING")
                .build();

        List<CustomerWithdrawalRequest> withdrawals = List.of(missingCustomer, insufficientBalance,
                bankMissing, bankBlank, clear, nullAmount, nullRequestedAt);

        when(customerWithdrawalRequestRepository.findByStatus(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(withdrawals));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(
                User.builder().id(customerIdInsufficient).fullName("Insufficient").phone("0900000002").build(),
                User.builder().id(customerIdBankMissing).fullName("Bank Missing").phone("0900000003").build(),
                User.builder().id(customerIdBankBlank).fullName("Bank Blank").phone("0900000004").build(),
                User.builder().id(customerIdClear).fullName("Clear").phone("0900000005").build(),
                User.builder().id(customerIdNullAmount).fullName("Null Amount").phone("0900000006").build(),
                User.builder().id(customerIdNullRequestedAt).fullName("Null Requested").phone("0900000007").build()
        ));
        when(walletRepository.findByCustomerIdIn(any())).thenReturn(List.of(
                CustomerWallet.builder().customerId(customerIdMissing).balance(new BigDecimal("1000000")).build(),
                CustomerWallet.builder().customerId(customerIdInsufficient).balance(new BigDecimal("100")).build(),
                CustomerWallet.builder().customerId(customerIdBankMissing).balance(new BigDecimal("1000000")).build(),
                CustomerWallet.builder().customerId(customerIdBankBlank).balance(new BigDecimal("1000000")).build(),
                CustomerWallet.builder().customerId(customerIdClear).balance(new BigDecimal("1000000")).build(),
                CustomerWallet.builder().customerId(customerIdNullRequestedAt).balance(new BigDecimal("1000000")).build()
                // customerIdNullAmount intentionally has no wallet entry -> wallet == null branch
        ));
        when(customerWithdrawalRequestRepository.findOldestPending(any(PageRequest.class)))
                .thenReturn(List.of(clear));
        when(customerWithdrawalRequestRepository.countPending()).thenReturn(7L);
        when(customerWithdrawalRequestRepository.sumPendingAmount()).thenReturn(new BigDecimal("5400100"));
        when(customerWithdrawalRequestRepository.countPendingRequestedBefore(any())).thenReturn(1L);

        PendingCustomerWithdrawalPageResponse response = service.getPending(0, 20);

        List<AdminCustomerWithdrawalItemResponse> content = response.content();
        assertThat(content).hasSize(7);

        AdminCustomerWithdrawalItemResponse missingItem = findByCustomerId(content, customerIdMissing);
        assertThat(missingItem.blockingReasons()).containsExactly("CUSTOMER_NOT_FOUND");
        assertThat(missingItem.processReady()).isFalse();
        assertThat(missingItem.bankAccountMasked()).isEqualTo("******7890");

        AdminCustomerWithdrawalItemResponse insufficientItem = findByCustomerId(content, customerIdInsufficient);
        assertThat(insufficientItem.blockingReasons()).containsExactly("INSUFFICIENT_CURRENT_BALANCE");

        AdminCustomerWithdrawalItemResponse bankMissingItem = findByCustomerId(content, customerIdBankMissing);
        assertThat(bankMissingItem.blockingReasons()).containsExactly("BANK_ACCOUNT_MISSING");
        assertThat(bankMissingItem.bankAccountMasked()).isNull();

        AdminCustomerWithdrawalItemResponse bankBlankItem = findByCustomerId(content, customerIdBankBlank);
        assertThat(bankBlankItem.blockingReasons()).containsExactly("BANK_ACCOUNT_MISSING");

        AdminCustomerWithdrawalItemResponse clearItem = findByCustomerId(content, customerIdClear);
        assertThat(clearItem.blockingReasons()).isEmpty();
        assertThat(clearItem.processReady()).isTrue();
        assertThat(clearItem.bankAccountMasked()).isEqualTo("******1234");
        assertThat(clearItem.daysWaiting()).isZero();

        AdminCustomerWithdrawalItemResponse nullAmountItem = findByCustomerId(content, customerIdNullAmount);
        assertThat(nullAmountItem.amount()).isEqualByComparingTo("0");
        assertThat(nullAmountItem.walletBalance()).isEqualByComparingTo("0");
        assertThat(nullAmountItem.blockingReasons()).isEmpty();

        AdminCustomerWithdrawalItemResponse nullRequestedAtItem = findByCustomerId(content, customerIdNullRequestedAt);
        assertThat(nullRequestedAtItem.daysWaiting()).isZero();

        assertThat(response.oldestWaitingDays()).isZero();
        assertThat(response.pendingCount()).isEqualTo(7L);
        assertThat(response.overSlaCount()).isEqualTo(1L);
    }

    private AdminCustomerWithdrawalItemResponse findByCustomerId(
            List<AdminCustomerWithdrawalItemResponse> content, UUID customerId) {
        return content.stream().filter(item -> item.customerId().equals(customerId)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing item for customer " + customerId));
    }

    // ---------- process ----------

    @Test
    void processDebitsWalletAndAppendsWithdrawalTransactionAndAudit() {
        UUID withdrawalId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User admin = admin();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(customerId).amount(new BigDecimal("1000000"))
                .bankCode("VCB").bankNameSnapshot("Vietcombank").bankAccountNumber("1234567890")
                .bankAccountHolder("NGUYEN VAN A").status("PENDING")
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId).balance(new BigDecimal("3000000"))
                .totalWithdrawn(new BigDecimal("500000")).build();

        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("VCB-20260609-001")).thenReturn(false);
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        service.process(withdrawalId, admin, new ProcessWithdrawalRequest("VCB-20260609-001", "Ghi chu"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("2000000");
        assertThat(wallet.getTotalWithdrawn()).isEqualByComparingTo("1500000");
        assertThat(withdrawal.getStatus()).isEqualTo("PROCESSED");
        assertThat(withdrawal.getProcessedBy()).isEqualTo(admin.getId());
        assertThat(withdrawal.getBankTxnRef()).isEqualTo("VCB-20260609-001");

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("-1000000");
        assertThat(transactionCaptor.getValue().getRelatedCustomerWithdrawalId()).isEqualTo(withdrawalId);
        assertThat(transactionCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("2000000");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CUSTOMER_WITHDRAWAL_PROCESSED");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void processThrowsNotFoundWhenWithdrawalMissing() {
        UUID withdrawalId = UUID.randomUUID();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

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
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").bankTxnRef("VCB-EXISTING").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(transactionRepository.findByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(Optional.of(Transaction.builder().balanceAfter(new BigDecimal("2500000")).build()));

        WithdrawalActionResponse response = service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("VCB-EXISTING", null));

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(response.balanceAfter()).isEqualByComparingTo("2500000");
        verify(walletRepository, never()).findByCustomerIdForUpdate(any());
    }

    @Test
    void processReplayReturnsNullBalanceWhenNoMatchingTransactionFound() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").bankTxnRef("VCB-EXISTING").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(transactionRepository.findByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(Optional.empty());

        WithdrawalActionResponse response = service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("VCB-EXISTING", null));

        assertThat(response.balanceAfter()).isNull();
    }

    @Test
    void processThrowsConflictWhenAlreadyProcessedWithDifferentBankTxnRef() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").bankTxnRef("OLD-REF").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictWhenBankTxnRefAlreadyUsed() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(true);

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictWhenWalletNotFound() {
        UUID withdrawalId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(customerId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsUnprocessableWhenBalanceInsufficient() {
        UUID withdrawalId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(customerId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        CustomerWallet wallet = CustomerWallet.builder().customerId(customerId).balance(new BigDecimal("100")).build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void processThrowsConflictWhenWithdrawalTransactionAlreadyExists() {
        UUID withdrawalId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(customerId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        CustomerWallet wallet = CustomerWallet.builder().customerId(customerId).balance(new BigDecimal("1000000")).build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processThrowsConflictOnDataIntegrityViolationDuringSave() {
        UUID withdrawalId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(customerId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        CustomerWallet wallet = CustomerWallet.builder().customerId(customerId).balance(new BigDecimal("1000000")).build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("dup")).when(transactionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.process(withdrawalId, admin(),
                new ProcessWithdrawalRequest("NEW-REF-01", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------- reject ----------

    @Test
    void rejectDoesNotMutateWalletOrAppendMoneyTransaction() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("700000"))
                .status("PENDING").build();

        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Thong tin ngan hang khong hop le"));

        assertThat(withdrawal.getStatus()).isEqualTo("REJECTED");
        assertThat(withdrawal.getRejectionReason()).isEqualTo("Thong tin ngan hang khong hop le");
        verify(walletRepository, never()).findByCustomerIdForUpdate(any());
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
        verify(auditLogRepository).saveAndFlush(any(AuditLog.class));
        verify(notificationRepository).save(any(Notification.class));
    }

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
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Ly do khong hop le")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectReplaysWhenAlreadyRejectedWithSameReason() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("REJECTED").rejectionReason("Ly do trung khop").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        WithdrawalActionResponse response = service.reject(withdrawalId, admin(),
                new RejectWithdrawalRequest("Ly do trung khop"));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(auditLogRepository, never()).saveAndFlush(any(AuditLog.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void rejectThrowsConflictWhenNotPending() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("PROCESSED").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

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
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(UUID.randomUUID()).amount(new BigDecimal("500000"))
                .status("REJECTED").rejectionReason("Ly do cu").build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.reject(withdrawalId, admin(), new RejectWithdrawalRequest("Ly do moi khac")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void processWritesAuditWithNoteAbsentWhenProcessingNoteIsNull() {
        UUID withdrawalId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder()
                .id(withdrawalId).customerId(customerId).amount(new BigDecimal("500000"))
                .status("PENDING").build();
        CustomerWallet wallet = CustomerWallet.builder().customerId(customerId).balance(new BigDecimal("1000000")).build();
        when(customerWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(customerWithdrawalRequestRepository.existsByBankTxnRef("NEW-REF-01")).thenReturn(false);
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByTypeAndRelatedCustomerWithdrawalId(TransactionType.WITHDRAWAL, withdrawalId))
                .thenReturn(false);

        service.process(withdrawalId, admin(), new ProcessWithdrawalRequest("NEW-REF-01", null));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDetail()).contains("note=none");
    }

    @Test
    void loadUsersReturnsEmptyMapWhenIdsCollectionIsNullViaDirectInvocation() {
        Map<?, ?> result = ReflectionTestUtils.invokeMethod(service, "loadUsers", (Object) null);

        assertThat(result).isEmpty();
    }

    @Test
    void loadWalletsReturnsEmptyMapWhenCustomerIdsSetIsNullViaDirectInvocation() {
        Map<?, ?> result = ReflectionTestUtils.invokeMethod(service, "loadWallets", (Object) null);

        assertThat(result).isEmpty();
    }

    @Test
    void daysBetweenReturnsZeroWhenToIsNullViaDirectInvocation() {
        Long result = ReflectionTestUtils.invokeMethod(service, "daysBetween",
                OffsetDateTime.now(ZoneOffset.UTC), null);

        assertThat(result).isZero();
    }

    @Test
    void writeAuditHandlesNullAdminViaDirectInvocation() {
        UUID withdrawalId = UUID.randomUUID();
        CustomerWithdrawalRequest withdrawal = CustomerWithdrawalRequest.builder().id(withdrawalId).build();

        ReflectionTestUtils.invokeMethod(service, "writeAudit", null, "TEST_ACTION", withdrawal, "detail");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActorId()).isNull();
        assertThat(auditCaptor.getValue().getActorEmail()).isNull();
    }
}
