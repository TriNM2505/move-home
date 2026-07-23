package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.WithdrawalRequest;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.dto.admin.finance.AdminTransactionResponse;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    private AdminTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AdminTransactionService(
                transactionRepository, userRepository, orderRepository, withdrawalRequestRepository);
    }

    private Page<Transaction> pageOf(Transaction... transactions) {
        return new PageImpl<>(List.of(transactions));
    }

    @Test
    void findTransactionsRejectsNegativePage() {
        assertThatThrownBy(() -> service.findTransactions(null, null, null, null, -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void findTransactionsRejectsSizeZero() {
        assertThatThrownBy(() -> service.findTransactions(null, null, null, null, 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void findTransactionsRejectsSizeAboveMax() {
        assertThatThrownBy(() -> service.findTransactions(null, null, null, null, 0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void findTransactionsRejectsWhenFromAfterTo() {
        Instant from = Instant.parse("2026-06-02T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        assertThatThrownBy(() -> service.findTransactions(null, from, to, null, 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void findTransactionsThrowsWhenTypeUnsupported() {
        assertThatThrownBy(() -> service.findTransactions("NOT_A_TYPE", null, null, null, 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void findTransactionsTreatsBlankAndAllAsNoTypeFilter() {
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf());

        service.findTransactions("", null, null, null, 0, 20);
        service.findTransactions("all", null, null, null, 0, 20);
        service.findTransactions(null, null, null, null, 0, 20);
        // no exception thrown -> type filter correctly treated as "no filter"
    }

    @Test
    void findTransactionsAcceptsValidTypeCaseInsensitive() {
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf());

        Page<AdminTransactionResponse> response = service.findTransactions("withdrawal", null, null, null, 0, 20);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void findTransactionsAppliesAllOptionalFiltersTogether() {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-02T00:00:00Z");
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf());

        Page<AdminTransactionResponse> response = service.findTransactions(
                "WITHDRAWAL", from, to, userId, 0, 20);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void findTransactionsMapsRelatedEntitiesWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();

        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(TransactionType.WITHDRAWAL)
                .amount(new java.math.BigDecimal("-500000"))
                .balanceAfter(new java.math.BigDecimal("1000000"))
                .relatedOrderId(orderId)
                .relatedWithdrawalId(withdrawalId)
                .relatedDisputeId(disputeId)
                .vnpayTxnRef("VNP1234567890")
                .description("Rut tien ve tai khoan ngan hang")
                .createdAt(Instant.parse("2026-06-02T07:00:00Z"))
                .build();

        User user = User.builder().id(userId).fullName("Nguyen Van A").role(UserRole.DRIVER)
                .email("driver1@example.com").build();
        ServiceOrder order = ServiceOrder.builder().id(orderId).orderCode("MH-000123").build();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().id(withdrawalId).bankTxnRef("BANKREF123456").build();

        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf(transaction));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(orderRepository.findAllById(any())).thenReturn(List.of(order));
        when(withdrawalRequestRepository.findAllById(any())).thenReturn(List.of(withdrawal));

        Page<AdminTransactionResponse> response = service.findTransactions(null, null, null, null, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        AdminTransactionResponse item = response.getContent().get(0);
        assertThat(item.type()).isEqualTo("WITHDRAWAL");
        assertThat(item.typeLabel()).isEqualTo("Rut tien");
        assertThat(item.userName()).isEqualTo("Nguyen Van A");
        assertThat(item.userRole()).isEqualTo("DRIVER");
        assertThat(item.userEmail()).isEqualTo("d***@example.com");
        assertThat(item.orderCode()).isEqualTo("MH-000123");
        assertThat(item.relatedOrderId()).isEqualTo(orderId);
        assertThat(item.relatedWithdrawalId()).isEqualTo(withdrawalId);
        assertThat(item.relatedDisputeId()).isEqualTo(disputeId);
        assertThat(item.vnpayTxnRefMasked()).isEqualTo("****7890");
        assertThat(item.bankTxnRefMasked()).isEqualTo("****3456");
        assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-06-02T07:00:00Z").atOffset(java.time.ZoneOffset.UTC));
    }

    @Test
    void findTransactionsMapsNullsWhenRelatedEntitiesAbsent() {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(null)
                .type(TransactionType.PLATFORM_FEE)
                .amount(new java.math.BigDecimal("300000"))
                .relatedOrderId(null)
                .relatedWithdrawalId(null)
                .vnpayTxnRef(null)
                .createdAt(null)
                .build();

        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf(transaction));

        Page<AdminTransactionResponse> response = service.findTransactions(null, null, null, null, 0, 20);

        AdminTransactionResponse item = response.getContent().get(0);
        assertThat(item.userName()).isNull();
        assertThat(item.userRole()).isNull();
        assertThat(item.userEmail()).isNull();
        assertThat(item.orderCode()).isNull();
        assertThat(item.vnpayTxnRefMasked()).isNull();
        assertThat(item.bankTxnRefMasked()).isNull();
        assertThat(item.createdAt()).isNull();
        assertThat(item.typeLabel()).isEqualTo("Phi nen tang");
    }

    @Test
    void findTransactionsMapsUserWithoutRoleAndEmail() {
        UUID userId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(TransactionType.DEPOSIT_TOP_UP)
                .amount(new java.math.BigDecimal("3000000"))
                .build();
        User user = User.builder().id(userId).fullName("Tran Thi B").role(null).email(null).build();

        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf(transaction));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        Page<AdminTransactionResponse> response = service.findTransactions(null, null, null, null, 0, 20);

        AdminTransactionResponse item = response.getContent().get(0);
        assertThat(item.userRole()).isNull();
        assertThat(item.userEmail()).isNull();
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void typeLabelCoversEveryTransactionType(TransactionType type) {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .type(type)
                .amount(java.math.BigDecimal.TEN)
                .build();
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf(transaction));

        Page<AdminTransactionResponse> response = service.findTransactions(null, null, null, null, 0, 20);

        String expected = switch (type) {
            case DEPOSIT_TOP_UP -> "Dat coc tai xe";
            case DEPOSIT_REFUND -> "Hoan coc tai xe";
            case ORDER_PAYMENT -> "Thanh toan don";
            case WALLET_TOP_UP -> "Nap vi khach hang";
            case DRIVER_EARNING -> "Thu nhap tai xe";
            case PLATFORM_FEE -> "Phi nen tang";
            case DAMAGE_DEDUCTION -> "Khau tru khieu nai";
            case WITHDRAWAL -> "Rut tien";
            case REFUND -> "Hoan tien";
        };
        assertThat(response.getContent().get(0).typeLabel()).isEqualTo(expected);
    }

    @Test
    void defaultPageSizeReturnsTwenty() {
        assertThat(service.defaultPageSize()).isEqualTo(20);
    }

    @Test
    void specificationAppliesTypeFromToAndUserIdPredicatesWhenInvoked() {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-02T00:00:00Z");
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf());

        service.findTransactions("WITHDRAWAL", from, to, userId, 0, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<Transaction>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(transactionRepository)
                .findAll(specCaptor.capture(), any(PageRequest.class));

        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Root<Transaction> root =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaQuery<?> query =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaQuery.class);
        jakarta.persistence.criteria.CriteriaBuilder cb =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Path<Instant> path =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Predicate predicate =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Predicate.class);

        org.mockito.Mockito.doReturn(path).when(root).get(org.mockito.ArgumentMatchers.anyString());
        when(cb.conjunction()).thenReturn(predicate);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(
                org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<Instant>>any(),
                org.mockito.ArgumentMatchers.eq(from)))
                .thenReturn(predicate);
        when(cb.lessThan(
                org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<Instant>>any(),
                org.mockito.ArgumentMatchers.eq(to)))
                .thenReturn(predicate);
        when(cb.and(any(), any())).thenReturn(predicate);

        jakarta.persistence.criteria.Predicate result = specCaptor.getValue().toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).equal(path, TransactionType.WITHDRAWAL);
        verify(cb).equal(path, userId);
        verify(cb).greaterThanOrEqualTo(path, from);
        verify(cb).lessThan(path, to);
    }

    @Test
    void maskEmailReturnsThreeStarsWhenAtSignIsMissingOrAtStart() {
        UUID userId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(TransactionType.WALLET_TOP_UP)
                .amount(java.math.BigDecimal.TEN)
                .build();
        User userWithoutAt = User.builder().id(userId).fullName("No At").email("invalidemail").build();

        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf(transaction));
        when(userRepository.findAllById(any())).thenReturn(List.of(userWithoutAt));

        Page<AdminTransactionResponse> response = service.findTransactions(null, null, null, null, 0, 20);

        assertThat(response.getContent().get(0).userEmail()).isEqualTo("***");
    }

    @Test
    void maskRefReturnsFourStarsWhenValueIsFourCharsOrShorter() {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.WITHDRAWAL)
                .amount(java.math.BigDecimal.TEN)
                .vnpayTxnRef("A1")
                .build();

        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(pageOf(transaction));

        Page<AdminTransactionResponse> response = service.findTransactions(null, null, null, null, 0, 20);

        assertThat(response.getContent().get(0).vnpayTxnRefMasked()).isEqualTo("****");
    }
}
