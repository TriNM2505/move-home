package vn.movehome.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.customer.finance.CreateCustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalItemResponse;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestRepository;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestResponse;
import vn.movehome.backend.dto.customer.wallet.TransactionDTO;
import vn.movehome.backend.dto.customer.wallet.WalletSummaryDTO;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.Notification;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.entity.WalletTransaction;
import vn.movehome.backend.repository.NotificationRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private WalletService service() {
        return new WalletService(
                walletRepository, transactionRepository, customerWithdrawalRequestRepository,
                userRepository, notificationRepository);
    }

    // ===== getOrCreateSummary =====

    @Test
    void getOrCreateSummaryReturnsExistingWalletWithHalfUpRounding() {
        UUID customerId = UUID.randomUUID();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("100000.50"))
                .totalToppedUp(new BigDecimal("200000"))
                .totalSpent(new BigDecimal("50000"))
                .totalWithdrawn(new BigDecimal("10000"))
                .build();
        when(walletRepository.findByCustomerId(customerId)).thenReturn(Optional.of(wallet));

        WalletSummaryDTO result = service().getOrCreateSummary(customerId);

        assertThat(result.balance()).isEqualByComparingTo("100001");
        assertThat(result.totalToppedUp()).isEqualByComparingTo("200000");
        assertThat(result.totalSpent()).isEqualByComparingTo("50000");
        assertThat(result.totalWithdrawn()).isEqualByComparingTo("10000");
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getOrCreateSummaryTreatsNullMoneyFieldsAsZero() {
        UUID customerId = UUID.randomUUID();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("50000"))
                .totalToppedUp(new BigDecimal("50000"))
                .totalSpent(null)
                .totalWithdrawn(null)
                .build();
        when(walletRepository.findByCustomerId(customerId)).thenReturn(Optional.of(wallet));

        WalletSummaryDTO result = service().getOrCreateSummary(customerId);

        assertThat(result.totalSpent()).isEqualByComparingTo("0");
        assertThat(result.totalWithdrawn()).isEqualByComparingTo("0");
    }

    @Test
    void getOrCreateSummaryCreatesWalletWhenMissing() {
        UUID customerId = UUID.randomUUID();
        when(walletRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        ArgumentCaptor<CustomerWallet> captor = ArgumentCaptor.forClass(CustomerWallet.class);
        when(walletRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        WalletSummaryDTO result = service().getOrCreateSummary(customerId);

        assertThat(result.balance()).isEqualByComparingTo("0");
        assertThat(result.totalToppedUp()).isEqualByComparingTo("0");
        assertThat(result.totalSpent()).isEqualByComparingTo("0");
        assertThat(result.totalWithdrawn()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
    }

    // ===== getTransactions =====

    @Test
    void getTransactionsMapsAllVnpayMaskingBranches() {
        UUID customerId = UUID.randomUUID();
        WalletTransaction nullRef = transaction("WALLET_TOP_UP", "1000000", null, null);
        WalletTransaction blankRef = transaction("WALLET_TOP_UP", "2000000", "   ", null);
        WalletTransaction shortRef = transaction("ORDER_PAYMENT", "3000000", "AB12", UUID.randomUUID());
        WalletTransaction longRef = transaction("ORDER_PAYMENT", "4000000", "TXN1234567890", UUID.randomUUID());
        Page<WalletTransaction> page = new PageImpl<>(List.of(nullRef, blankRef, shortRef, longRef));
        when(transactionRepository.findByUserId(eq(customerId), any(PageRequest.class))).thenReturn(page);

        Page<TransactionDTO> result = service().getTransactions(customerId, 0, 10);

        List<TransactionDTO> content = result.getContent();
        assertThat(content).hasSize(4);
        assertThat(content.get(0).vnpayTxnRefMasked()).isNull();
        assertThat(content.get(1).vnpayTxnRefMasked()).isNull();
        assertThat(content.get(2).vnpayTxnRefMasked()).isEqualTo("****");
        assertThat(content.get(3).vnpayTxnRefMasked()).isEqualTo("****7890");
        assertThat(content.get(0).balanceAfter()).isNull();
        assertThat(content.get(2).relatedOrderId()).isEqualTo(shortRef.getRelatedOrderId());
    }

    @Test
    void getTransactionsRejectsNegativePage() {
        assertThatThrownBy(() -> service().getTransactions(UUID.randomUUID(), -1, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số trang không hợp lệ.");
                });
    }

    @Test
    void getTransactionsRejectsZeroSize() {
        assertThatThrownBy(() -> service().getTransactions(UUID.randomUUID(), 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100."));
    }

    @Test
    void getTransactionsRejectsOversizedPage() {
        assertThatThrownBy(() -> service().getTransactions(UUID.randomUUID(), 0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100."));
    }

    private WalletTransaction transaction(String type, String amount, String vnpayTxnRef, UUID relatedOrderId) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUserId(UUID.randomUUID());
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setRelatedOrderId(relatedOrderId);
        transaction.setDescription("desc-" + type);
        transaction.setVnpayTxnRef(vnpayTxnRef);
        transaction.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return transaction;
    }

    // ===== createWithdrawal - validation branches =====

    @Test
    void createWithdrawalRejectsNullRequest() {
        User customer = customer("Nguyen Van A");
        assertThatThrownBy(() -> service().createWithdrawal(customer, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập số tiền cần rút."));
    }

    @Test
    void createWithdrawalRejectsNullAmount() {
        User customer = customer("Nguyen Van A");
        CreateCustomerWithdrawalRequest request = new CreateCustomerWithdrawalRequest(null, "VCB", "12345678");
        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập số tiền cần rút."));
    }

    @Test
    void createWithdrawalRejectsFractionalAmount() {
        User customer = customer("Nguyen Van A");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("500000.5"), "VCB", "12345678");
        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số tiền rút phải là VND nguyên đồng."));
    }

    @Test
    void createWithdrawalRejectsNonPositiveAmount() {
        User customer = customer("Nguyen Van A");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(BigDecimal.ZERO, "VCB", "12345678");
        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số tiền rút phải lớn hơn 0."));
    }

    @Test
    void createWithdrawalRejectsWhenWalletNotFound() {
        User customer = customer("Nguyen Van A");
        when(walletRepository.findByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.empty());
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "VCB", "12345678");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("CUSTOMER_WALLET_NOT_FOUND|Không tìm thấy ví khách hàng.");
                });
    }

    @Test
    void createWithdrawalRejectsWhenAmountExceedsAvailableBalance() {
        User customer = customer("Nguyen Van A");
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customer.getId())
                .balance(new BigDecimal("100000"))
                .build();
        when(walletRepository.findByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.of(wallet));
        when(customerWithdrawalRequestRepository.findPendingByCustomerIdForUpdate(customer.getId()))
                .thenReturn(Collections.emptyList());
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("200000"), "VCB", "12345678");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("INSUFFICIENT_AVAILABLE_BALANCE|Số tiền rút vượt quá số dư khả dụng.");
                });
    }

    @Test
    void createWithdrawalClampsAvailableToZeroWhenPendingExceedsBalance() {
        User customer = customer("Nguyen Van A");
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customer.getId())
                .balance(new BigDecimal("100000"))
                .build();
        when(walletRepository.findByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.of(wallet));

        CustomerWithdrawalRequest pendingWithNullAmount = CustomerWithdrawalRequest.builder()
                .customerId(customer.getId())
                .amount(null)
                .status("PENDING")
                .build();
        CustomerWithdrawalRequest pendingLarge = CustomerWithdrawalRequest.builder()
                .customerId(customer.getId())
                .amount(new BigDecimal("500000"))
                .status("PENDING")
                .build();
        when(customerWithdrawalRequestRepository.findPendingByCustomerIdForUpdate(customer.getId()))
                .thenReturn(Arrays.asList(pendingWithNullAmount, pendingLarge));

        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("1"), "VCB", "12345678");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("INSUFFICIENT_AVAILABLE_BALANCE|Số tiền rút vượt quá số dư khả dụng."));
    }

    @Test
    void createWithdrawalRejectsNullBankCode() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), null, "12345678");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng chọn ngân hàng nhận tiền."));
    }

    @Test
    void createWithdrawalRejectsBlankBankCode() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), " ", "12345678");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng chọn ngân hàng nhận tiền."));
    }

    @Test
    void createWithdrawalRejectsUnsupportedBankCode() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "XYZ", "12345678");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Ngân hàng không được hỗ trợ."));
    }

    @Test
    void createWithdrawalRejectsBlankBankAccountNumber() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", " ");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập số tài khoản ngân hàng."));
    }

    @Test
    void createWithdrawalRejectsNullBankAccountNumber() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", null);

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập số tài khoản ngân hàng."));
    }

    @Test
    void createWithdrawalRejectsInvalidBankAccountNumberPattern() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", "123");

        assertThatThrownBy(() -> service().createWithdrawal(customer, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(
                                "VALIDATION_ERROR|Số tài khoản không hợp lệ (phải gồm 8 đến 15 chữ số)."));
    }

    // ===== createWithdrawal - success paths =====

    @Test
    void createWithdrawalSucceedsAndNotifiesAdminsWithCustomerName() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");

        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        CustomerWithdrawalRequest saved = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .customerId(customer.getId())
                .amount(new BigDecimal("100000"))
                .status("PENDING")
                .requestedAt(requestedAt)
                .build();
        ArgumentCaptor<CustomerWithdrawalRequest> withdrawalCaptor =
                ArgumentCaptor.forClass(CustomerWithdrawalRequest.class);
        when(customerWithdrawalRequestRepository.saveAndFlush(withdrawalCaptor.capture())).thenReturn(saved);

        User admin = User.builder().id(UUID.randomUUID()).email("admin@movehome.vn").build();
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));

        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", "12345678");

        CustomerWithdrawalRequestResponse response = service().createWithdrawal(customer, request);

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.amount()).isEqualByComparingTo("100000");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).isEqualTo("Yêu cầu rút tiền đã được gửi.");
        assertThat(response.requestedAt()).isEqualTo(requestedAt);

        CustomerWithdrawalRequest submitted = withdrawalCaptor.getValue();
        assertThat(submitted.getBankCode()).isEqualTo("VCB");
        assertThat(submitted.getBankNameSnapshot()).isEqualTo("Vietcombank");
        assertThat(submitted.getBankAccountHolder()).isEqualTo("NGUYEN VAN A");

        ArgumentCaptor<List<Notification>> notificationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<Notification> notifications = notificationsCaptor.getValue();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getUserId()).isEqualTo(admin.getId());
        assertThat(notification.getType()).isEqualTo(NotificationType.WITHDRAWAL_REQUESTED);
        assertThat(notification.getTitle()).isEqualTo("Yêu cầu rút tiền mới (khách hàng)");
        assertThat(notification.getMessage())
                .isEqualTo("Khách hàng Nguyen Van A yêu cầu rút 100000 VND. Vui lòng xử lý.");
    }

    @Test
    void createWithdrawalFallsBackToDefaultCustomerNameWhenFullNameMissing() {
        User customer = customer(null);
        stubWalletForWithdrawal(customer.getId(), "500000");

        CustomerWithdrawalRequest saved = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .customerId(customer.getId())
                .amount(new BigDecimal("100000"))
                .status("PENDING")
                .build();
        when(customerWithdrawalRequestRepository.saveAndFlush(any())).thenReturn(saved);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", "12345678");

        CustomerWithdrawalRequestResponse response = service().createWithdrawal(customer, request);

        // requestedAt null tren saved -> fallback OffsetDateTime.now(UTC)
        assertThat(response.requestedAt()).isNotNull();
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void createWithdrawalSwallowsNotificationFailureAndStillReturnsResponse() {
        User customer = customer("Nguyen Van A");
        stubWalletForWithdrawal(customer.getId(), "500000");

        CustomerWithdrawalRequest saved = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .customerId(customer.getId())
                .amount(new BigDecimal("100000"))
                .status("PENDING")
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        when(customerWithdrawalRequestRepository.saveAndFlush(any())).thenReturn(saved);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenThrow(new RuntimeException("db down"));

        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", "12345678");

        CustomerWithdrawalRequestResponse response = service().createWithdrawal(customer, request);

        assertThat(response.id()).isEqualTo(saved.getId());
        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    void createWithdrawalNormalizesBlankFullNameToDefaultAccountHolder() {
        User customer = customer("   ");
        stubWalletForWithdrawal(customer.getId(), "500000");

        ArgumentCaptor<CustomerWithdrawalRequest> withdrawalCaptor =
                ArgumentCaptor.forClass(CustomerWithdrawalRequest.class);
        CustomerWithdrawalRequest saved = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .customerId(customer.getId())
                .amount(new BigDecimal("100000"))
                .status("PENDING")
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        when(customerWithdrawalRequestRepository.saveAndFlush(withdrawalCaptor.capture())).thenReturn(saved);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", "12345678");

        service().createWithdrawal(customer, request);

        assertThat(withdrawalCaptor.getValue().getBankAccountHolder()).isEqualTo("CHUA CAP NHAT");
    }

    @Test
    void createWithdrawalTruncatesLongAccountHolderNameAndCollapsesWhitespace() {
        String longName = "  nguyen   van a".repeat(10); // whitespace + lowercase, > 100 ky tu sau khi collapse
        User customer = customer(longName);
        stubWalletForWithdrawal(customer.getId(), "500000");

        ArgumentCaptor<CustomerWithdrawalRequest> withdrawalCaptor =
                ArgumentCaptor.forClass(CustomerWithdrawalRequest.class);
        CustomerWithdrawalRequest saved = CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .customerId(customer.getId())
                .amount(new BigDecimal("100000"))
                .status("PENDING")
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        when(customerWithdrawalRequestRepository.saveAndFlush(withdrawalCaptor.capture())).thenReturn(saved);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "vcb", "12345678");

        service().createWithdrawal(customer, request);

        String holder = withdrawalCaptor.getValue().getBankAccountHolder();
        assertThat(holder).hasSize(100);
        assertThat(holder).isEqualTo(holder.toUpperCase());
        assertThat(holder).doesNotContain("  ");
    }

    private void stubWalletForWithdrawal(UUID customerId, String balance) {
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal(balance))
                .build();
        when(walletRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(wallet));
        when(customerWithdrawalRequestRepository.findPendingByCustomerIdForUpdate(customerId))
                .thenReturn(Collections.emptyList());
    }

    private User customer(String fullName) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .fullName(fullName)
                .build();
    }

    // ===== getWithdrawals =====

    @Test
    void getWithdrawalsMapsMaskedAccountAndFallbackFieldsAcrossVariants() {
        UUID customerId = UUID.randomUUID();
        CustomerWithdrawalRequest nullAccount = withdrawalItem(customerId, null, null);
        CustomerWithdrawalRequest blankAccount = withdrawalItem(customerId, "   ", "Từ chối do sai thông tin");
        CustomerWithdrawalRequest shortAccount = withdrawalItem(customerId, "1234", null);
        CustomerWithdrawalRequest longAccount = withdrawalItem(customerId, "00112233445566", null);
        CustomerWithdrawalRequest nullAmount = withdrawalItem(customerId, "12345678", null);
        nullAmount.setAmount(null);
        Page<CustomerWithdrawalRequest> page =
                new PageImpl<>(List.of(nullAccount, blankAccount, shortAccount, longAccount, nullAmount));
        when(customerWithdrawalRequestRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(page);

        Page<CustomerWithdrawalItemResponse> result = service().getWithdrawals(customerId, 0, 10);

        List<CustomerWithdrawalItemResponse> content = result.getContent();
        assertThat(content).hasSize(5);
        assertThat(content.get(0).bankAccountMasked()).isNull();
        assertThat(content.get(1).bankAccountMasked()).isNull();
        assertThat(content.get(1).rejectionReason()).isEqualTo("Từ chối do sai thông tin");
        assertThat(content.get(2).bankAccountMasked()).isEqualTo("******1234");
        assertThat(content.get(3).bankAccountMasked()).isEqualTo("******5566");
        assertThat(content.get(4).amount()).isEqualByComparingTo("0");
    }

    @Test
    void getWithdrawalsRejectsInvalidPageSize() {
        assertThatThrownBy(() -> service().getWithdrawals(UUID.randomUUID(), 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100."));
    }

    private CustomerWithdrawalRequest withdrawalItem(UUID customerId, String bankAccountNumber, String rejectionReason) {
        return CustomerWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .amount(new BigDecimal("100000"))
                .bankCode("VCB")
                .bankNameSnapshot("Vietcombank")
                .bankAccountNumber(bankAccountNumber)
                .bankAccountHolder("NGUYEN VAN A")
                .status("PENDING")
                .rejectionReason(rejectionReason)
                .requestedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    // ===== defaultPageSize =====

    @Test
    void defaultPageSizeReturnsTwenty() {
        assertThat(service().defaultPageSize()).isEqualTo(20);
    }
}
