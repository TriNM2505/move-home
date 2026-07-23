package vn.movehome.backend.dispute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusTransitionService orderStatusTransitionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRefundService customerRefundService;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private DriverEarningService driverEarningService;

    @Mock
    private vn.movehome.backend.driver.finance.DriverWalletRepository driverWalletRepository;

    @Mock
    private vn.movehome.backend.repository.TransactionRepository transactionRepository;

    @Mock
    private vn.movehome.backend.repository.DriverProfileRepository driverProfileRepository;

    @Mock
    private DisputePhotoService disputePhotoService;

    private DisputeService service;

    @BeforeEach
    void setUp() {
        service = new DisputeService(
                disputeRepository,
                orderRepository,
                orderStatusTransitionService,
                userRepository,
                customerRefundService,
                auditService,
                notificationService,
                new ObjectMapper(),
                driverEarningService,
                driverWalletRepository,
                transactionRepository,
                driverProfileRepository,
                disputePhotoService);
    }

    @Test
    void createFromCompletedOrderTransitionsToDisputedAndNotifies() {
        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        User manager = user(UserRole.MANAGER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));
        when(disputeRepository.existsByOrderIdAndStatusIn(eq(orderId), anyCollection()))
                .thenReturn(false);
        when(orderStatusTransitionService.transition(eq(order), eq("DISPUTED"), eq(customer.getId()),
                eq(UserRole.CUSTOMER.name()), any(OffsetDateTime.class)))
                .thenAnswer(invocation -> {
                    order.setStatus("DISPUTED");
                    return order;
                });
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            dispute.setId(disputeId);
            return dispute;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(manager));
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        DisputeActionResponse response = service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE",
                new BigDecimal("700000"),
                "Tu go bi tray xuoc sau khi van chuyen"));

        assertThat(response.id()).isEqualTo(disputeId);
        assertThat(response.orderStatus()).isEqualTo("DISPUTED");
        assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);

        ArgumentCaptor<Dispute> disputeCaptor = ArgumentCaptor.forClass(Dispute.class);
        verify(disputeRepository).saveAndFlush(disputeCaptor.capture());
        assertThat(disputeCaptor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(disputeCaptor.getValue().getClaimType()).isEqualTo("DAMAGE");
        assertThat(disputeCaptor.getValue().getClaimAmount()).isEqualByComparingTo("700000");

        verify(notificationService).create(eq(customer.getId()), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
        verify(notificationService).create(eq(order.getDriverId()), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
        verify(notificationService).create(eq(manager.getId()), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
        verify(auditService).log(eq(customer.getId()), eq(customer.getEmail()), eq("DISPUTE_OPENED"),
                eq("DISPUTE"), eq(disputeId.toString()), anyString());
    }

    @Test
    void createFromNonCompletedOrderReturnsConflictWithoutMutation() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());
        order.setStatus("IN_PROGRESS");

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE",
                new BigDecimal("700000"),
                "Tu go bi tray xuoc sau khi van chuyen")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("INVALID_ORDER_DISPUTE_STATE|");
                });

        verify(orderStatusTransitionService, never()).transition(any(), anyString(), any(), anyString(), any());
        verify(disputeRepository, never()).saveAndFlush(any(Dispute.class));
    }

    @Test
    void resolveRefundsCustomerAndMarksDisputeResolved() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User manager = user(UserRole.MANAGER);
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(customerRefundService.refundForDispute(dispute.getCustomerId(), orderId, disputeId,
                new BigDecimal("500000"), "Hoan tien khieu nai don MH202606260001"))
                .thenReturn(new BigDecimal("1500000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisputeActionResponse response = service.resolve(disputeId, manager, new ResolveDisputeRequest(
                new BigDecimal("500000"),
                "Bang chung hop le, hoan tien cho khach"));

        assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_REFUND);
        assertThat(response.resolutionAmount()).isEqualByComparingTo("500000");
        assertThat(dispute.getResolvedBy()).isEqualTo(manager.getId());
        verify(customerRefundService).refundForDispute(dispute.getCustomerId(), orderId, disputeId,
                new BigDecimal("500000"), "Hoan tien khieu nai don MH202606260001");
        verify(auditService).log(eq(manager.getId()), eq(manager.getEmail()), eq("DISPUTE_RESOLVED"),
                eq("DISPUTE"), eq(disputeId.toString()), anyString());
        verify(notificationService).create(eq(dispute.getCustomerId()), eq(NotificationType.DISPUTE_RESOLVED),
                anyString(), anyString());
        verify(notificationService).create(eq(dispute.getDriverId()), eq(NotificationType.DISPUTE_RESOLVED),
                anyString(), anyString());
    }

    @Test
    void rejectClosesWithoutRefund() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User manager = user(UserRole.MANAGER);
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisputeActionResponse response = service.reject(disputeId, manager,
                new RejectDisputeRequest("Bang chung khong du de chap nhan khieu nai"));

        assertThat(response.status()).isEqualTo(DisputeStatus.CLOSED_NO_FAULT);
        assertThat(response.resolutionAmount()).isNull();
        verify(customerRefundService, never()).refundForDispute(any(), any(), any(), any(), anyString());
        verify(auditService).log(eq(manager.getId()), eq(manager.getEmail()), eq("DISPUTE_REJECTED"),
                eq("DISPUTE"), eq(disputeId.toString()), anyString());
        verify(notificationService).create(eq(dispute.getCustomerId()), eq(NotificationType.DISPUTE_REJECTED),
                anyString(), anyString());
    }

    @Test
    void returnOrderToCompletedDoesNothingWhenOrderAlreadyCancelled() {
        // Nhanh rieng cua returnOrderToCompleted(): don da CANCELLED (vd khieu nai doi chieu
        // tien-chuyen) thi KHONG dua ve COMPLETED va KHONG goi save.
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = disputedOrder(orderId, UUID.randomUUID(), UUID.randomUUID());
        order.setStatus("CANCELLED");

        ReflectionTestUtils.invokeMethod(service, "returnOrderToCompleted", order);

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        verify(orderRepository, never()).save(any(ServiceOrder.class));
    }

    @Test
    void safeNotifyDoesNothingWhenUserIdIsNull() throws Exception {
        // Nhanh rieng cua safeNotify(): userId null -> return som, khong goi notificationService.
        // Dung java.lang.reflect truc tiep (khong dung ReflectionTestUtils.invokeMethod) vi tham so
        // dau tien null khien Spring khong xac dinh duoc kieu tham so de tim method bang toTypeArray().
        java.lang.reflect.Method safeNotify = DisputeService.class.getDeclaredMethod(
                "safeNotify", UUID.class, String.class, String.class, String.class);
        safeNotify.setAccessible(true);
        safeNotify.invoke(service, null, NotificationType.DISPUTE_OPENED, "Tieu de", "Noi dung");

        verify(notificationService, never()).create(any(), any(), anyString(), anyString());
    }

    @Test
    void safeNotifySwallowsExceptionFromNotificationService() {
        // Nhanh catch(Exception): loi tao notification khong duoc lam fail luong nghiep vu.
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(notificationService).create(eq(userId), eq(NotificationType.DISPUTE_OPENED),
                        anyString(), anyString());

        ReflectionTestUtils.invokeMethod(service, "safeNotify",
                userId, NotificationType.DISPUTE_OPENED, "Tieu de", "Noi dung");

        verify(notificationService).create(eq(userId), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
    }

    // ===================== create() — them cac nhanh loi/validate =====================

    @Test
    void createWithoutAuthenticatedUserThrowsUnauthorized() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), null,
                new CreateDisputeRequest("DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(401);
                    assertThat(ex.getReason()).startsWith("AUTHENTICATION_REQUIRED|");
                });
    }

    @Test
    void createWithInvalidClaimTypeThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("FOO_BAR", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_CLAIM_TYPE|");
                });
    }

    @Test
    void createWithTooShortStatementThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("DAMAGE", new BigDecimal("100000"), "qua ngan")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_CUSTOMER_STATEMENT|");
                });
    }

    @Test
    void createWithStatementTooLongThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        String longStatement = "a".repeat(2001);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("DAMAGE", new BigDecimal("100000"), longStatement)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_CUSTOMER_STATEMENT|");
                });
    }

    @Test
    void createWithStatementWithoutLettersThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("DAMAGE", new BigDecimal("100000"), "1234567890123")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_CUSTOMER_STATEMENT|");
                });
    }

    @Test
    void createWithNonPositiveClaimAmountThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("DAMAGE", BigDecimal.ZERO, "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_AMOUNT|");
                });
    }

    @Test
    void createWithNullClaimAmountThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("DAMAGE", null, "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_AMOUNT|");
                });
    }

    @Test
    void createWithNonIntegerClaimAmountThrowsUnprocessableEntity() {
        User customer = user(UserRole.CUSTOMER);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), customer,
                new CreateDisputeRequest("DAMAGE", new BigDecimal("100000.50"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_AMOUNT|");
                });
    }

    @Test
    void createWithMissingOrderThrowsNotFound() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    @Test
    void createAfterEscrowWindowClosedThrowsConflictWithHourMessage() {
        ReflectionTestUtils.setField(service, "escrowHoldMinutes", 120L);
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());
        order.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DISPUTE_WINDOW_CLOSED|");
                    assertThat(ex.getReason()).contains("2 giờ");
                });
    }

    @Test
    void createAfterEscrowWindowClosedThrowsConflictWithMinuteMessage() {
        ReflectionTestUtils.setField(service, "escrowHoldMinutes", 90L);
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());
        order.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).contains("90 phút");
                });
    }

    @Test
    void createWithNullCompletedAtSkipsEscrowWindowCheck() {
        ReflectionTestUtils.setField(service, "escrowHoldMinutes", 120L);
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), null);
        order.setCompletedAt(null);

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("ORDER_DRIVER_REQUIRED|");
                });
    }

    @Test
    void createWithoutDriverThrowsConflict() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), null);

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("ORDER_DRIVER_REQUIRED|");
                });
    }

    @Test
    void createWithClaimAmountExceedingOrderTotalThrowsUnprocessableEntity() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("2000000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("CLAIM_AMOUNT_EXCEEDS_ORDER_TOTAL|");
                });
    }

    @Test
    void createWhenDisputeAlreadyOpenThrowsConflict() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));
        when(disputeRepository.existsByOrderIdAndStatusIn(eq(orderId), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DISPUTE_ALREADY_OPEN|");
                });
        verify(orderStatusTransitionService, never()).transition(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void createWhenSaveThrowsDataIntegrityViolationMapsToConflict() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UserRole.CUSTOMER);
        ServiceOrder order = completedOrder(orderId, customer.getId(), UUID.randomUUID());

        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId()))
                .thenReturn(Optional.of(order));
        when(disputeRepository.existsByOrderIdAndStatusIn(eq(orderId), anyCollection()))
                .thenReturn(false);
        when(orderStatusTransitionService.transition(eq(order), eq("DISPUTED"), eq(customer.getId()),
                eq(UserRole.CUSTOMER.name()), any(OffsetDateTime.class)))
                .thenAnswer(invocation -> {
                    order.setStatus("DISPUTED");
                    return order;
                });
        when(disputeRepository.saveAndFlush(any(Dispute.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(orderId, customer, new CreateDisputeRequest(
                "DAMAGE", new BigDecimal("100000"), "Do dac bi hu hong nghiem trong")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DISPUTE_ALREADY_OPEN|");
                });
    }

    // ===================== list() =====================

    @Test
    void listWithNegativePageThrowsUnprocessableEntity() {
        assertThatThrownBy(() -> service.list(null, -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("VALIDATION_ERROR|");
                });
    }

    @Test
    void listWithZeroSizeThrowsUnprocessableEntity() {
        assertThatThrownBy(() -> service.list(null, 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("VALIDATION_ERROR|");
                });
    }

    @Test
    void listWithOversizedPageThrowsUnprocessableEntity() {
        assertThatThrownBy(() -> service.list(null, 0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("VALIDATION_ERROR|");
                });
    }

    @Test
    void listWithInvalidStatusFilterThrowsUnprocessableEntity() {
        assertThatThrownBy(() -> service.list("NOT_A_STATUS", 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_DISPUTE_FILTER|");
                });
    }

    @Test
    void listWithBlankStatusFilterTreatsAsNoFilter() {
        when(disputeRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        Page<DisputeListItemResponse> result = service.list("   ", 0, 20);

        assertThat(result.getContent()).isEmpty();
        verify(disputeRepository, never()).findByStatus(anyString(), any());
    }

    @Test
    void listWithoutStatusFilterUsesFindAllAndMapsMissingOrderAndUsersToNull() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        Page<Dispute> page = new PageImpl<>(List.of(dispute), PageRequest.of(0, 20), 1);

        when(disputeRepository.findAll(any(PageRequest.class))).thenReturn(page);
        when(orderRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());

        Page<DisputeListItemResponse> result = service.list(null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        DisputeListItemResponse item = result.getContent().get(0);
        assertThat(item.orderCode()).isNull();
        assertThat(item.orderStatus()).isNull();
        assertThat(item.customerName()).isNull();
        assertThat(item.driverName()).isNull();
        verify(disputeRepository, never()).findByStatus(anyString(), any());
    }

    @Test
    void listWithStatusFilterUsesFindByStatusAndMapsOrderAndUsers() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        User customer = userWithId(dispute.getCustomerId(), UserRole.CUSTOMER);
        User driver = userWithId(dispute.getDriverId(), UserRole.DRIVER);
        Page<Dispute> page = new PageImpl<>(List.of(dispute), PageRequest.of(0, 20), 1);

        when(disputeRepository.findByStatus(eq(DisputeStatus.OPEN), any(PageRequest.class))).thenReturn(page);
        when(orderRepository.findAllById(anyCollection())).thenReturn(List.of(order));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(customer, driver));

        Page<DisputeListItemResponse> result = service.list("open", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        DisputeListItemResponse item = result.getContent().get(0);
        assertThat(item.orderCode()).isEqualTo(order.getOrderCode());
        assertThat(item.orderStatus()).isEqualTo(order.getStatus());
        assertThat(item.customerName()).isEqualTo(customer.getFullName());
        assertThat(item.driverName()).isEqualTo(driver.getFullName());
        verify(disputeRepository, never()).findAll(any(PageRequest.class));
    }

    // ===================== detail() =====================

    @Test
    void detailWhenDisputeNotFoundThrowsNotFound() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("DISPUTE_NOT_FOUND|");
                });
    }

    @Test
    void detailWhenOrderNotFoundThrowsNotFound() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    @Test
    void detailReturnsFullResponseAndTreatsMissingDriverAsNull() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        User customer = userWithId(dispute.getCustomerId(), UserRole.CUSTOMER);

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(customer));
        when(disputePhotoService.signedUrls(disputeId)).thenReturn(List.of("https://cdn/photo1.jpg"));

        DisputeDetailResponse response = service.detail(disputeId);

        assertThat(response.id()).isEqualTo(disputeId);
        assertThat(response.customer().fullName()).isEqualTo(customer.getFullName());
        assertThat(response.driver().fullName()).isNull();
        assertThat(response.photoUrls()).containsExactly("https://cdn/photo1.jpg");
        assertThat(response.order().orderCode()).isEqualTo(order.getOrderCode());
    }

    // ===================== resolve() — them cac nhanh loi/validate =====================

    @Test
    void resolveWithoutAuthenticatedActorThrowsUnauthorized() {
        assertThatThrownBy(() -> service.resolve(UUID.randomUUID(), null,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void resolveWithInvalidNoteThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        assertThatThrownBy(() -> service.resolve(UUID.randomUUID(), manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "ngan")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_NOTE|");
                });
    }

    @Test
    void resolveWithNonPositiveRefundAmountThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        assertThatThrownBy(() -> service.resolve(UUID.randomUUID(), manager,
                new ResolveDisputeRequest(BigDecimal.ZERO, "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_AMOUNT|");
                });
    }

    @Test
    void resolveWhenDisputeNotFoundThrowsNotFound() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("DISPUTE_NOT_FOUND|");
                });
    }

    @Test
    void resolveWhenDisputeAlreadyResolvedThrowsConflict() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        dispute.setStatus(DisputeStatus.RESOLVED_REFUND);
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DISPUTE_ALREADY_RESOLVED|");
                });
    }

    @Test
    void resolveWhenOrderNotFoundThrowsNotFound() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    @Test
    void resolveWhenOrderNotInDisputedStateThrowsConflict() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = completedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("INVALID_ORDER_DISPUTE_STATE|");
                });
    }

    @Test
    void resolveWithRefundExceedingOrderTotalThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("2000000"), "Bang chung hop le de hoan tien")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("AMOUNT_EXCEEDS_ORDER_TOTAL|");
                });
    }

    @Test
    void resolveSkipsEarningReleaseWhenOrderHasNoDriver() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), null);
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien"));

        assertThat(response.orderStatus()).isEqualTo("COMPLETED");
        verify(driverEarningService, never()).creditEarning(any());
        verify(orderRepository).save(order);
    }

    @Test
    void resolveSkipsEarningReleaseWhenAlreadyReleased() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        order.setEarningReleasedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolve(disputeId, manager,
                new ResolveDisputeRequest(new BigDecimal("100000"), "Bang chung hop le de hoan tien"));

        verify(driverEarningService, never()).creditEarning(any());
    }

    // ===================== openMismatchDispute() =====================

    @Test
    void openMismatchDisputeWithoutDriverThrowsConflict() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = disputedOrder(orderId, customerId, null);

        assertThatThrownBy(() -> service.openMismatchDispute(order, customerId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("ORDER_DRIVER_REQUIRED|");
                });
    }

    @Test
    void openMismatchDisputeCreatesDisputeWithDepositClaimAndNotifiesOperations() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setStatus("CANCELLED");
        order.setTotalQuote(new BigDecimal("1000000"));
        order.setCommissionRateSnapshot(new BigDecimal("0.3000"));
        User manager = user(UserRole.MANAGER);
        User admin = user(UserRole.ADMIN);

        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(manager));
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));

        service.openMismatchDispute(order, customerId);

        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        verify(disputeRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClaimType()).isEqualTo("DRIVER_MISMATCH");
        assertThat(captor.getValue().getClaimAmount()).isEqualByComparingTo("300000");
        verify(auditService).log(eq(customerId), isNull(), eq("MISMATCH_DISPUTE_OPENED"), eq("DISPUTE"),
                anyString(), anyString());
        verify(notificationService).create(eq(manager.getId()), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
        verify(notificationService).create(eq(admin.getId()), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
        verify(notificationService).create(eq(driverId), eq(NotificationType.DISPUTE_OPENED),
                anyString(), anyString());
    }

    @Test
    void openMismatchDisputeUsesDefaultCommissionRateAndZeroTotalWhenMissing() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setTotalQuote(null);
        order.setCommissionRateSnapshot(null);

        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of());
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        service.openMismatchDispute(order, customerId);

        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        verify(disputeRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClaimAmount()).isEqualByComparingTo("0");
    }

    @Test
    void openMismatchDisputeIgnoresDuplicateDataIntegrityViolation() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);

        when(disputeRepository.saveAndFlush(any(Dispute.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        service.openMismatchDispute(order, customerId);

        verify(auditService, never()).log(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).create(any(), anyString(), anyString(), anyString());
    }

    // ===================== resolveMismatch() =====================

    @Test
    void resolveMismatchWithoutAuthenticatedActorThrowsUnauthorized() {
        assertThatThrownBy(() -> service.resolveMismatch(UUID.randomUUID(), null, true,
                "Da xac minh voi tai xe ve su co nay"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void resolveMismatchWithInvalidNoteThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        assertThatThrownBy(() -> service.resolveMismatch(UUID.randomUUID(), manager, true, "ngan"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_NOTE|");
                });
    }

    @Test
    void resolveMismatchOnNonMismatchDisputeThrowsConflict() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.resolveMismatch(disputeId, manager, true,
                "Da xac minh voi tai xe ve su co nay"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("NOT_MISMATCH_DISPUTE|");
                });
    }

    @Test
    void resolveMismatchWhenOrderNotFoundThrowsNotFound() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = mismatchDispute(disputeId, orderId, UUID.randomUUID(), UUID.randomUUID());
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveMismatch(disputeId, manager, true,
                "Da xac minh voi tai xe ve su co nay"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    @Test
    void resolveMismatchRejectClosesWithoutRefundOrPenalty() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = mismatchDispute(disputeId, orderId, customerId, driverId);
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setStatus("CANCELLED");
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolveMismatch(disputeId, manager, false,
                "Da xac minh voi tai xe ve su co nay");

        assertThat(response.status()).isEqualTo(DisputeStatus.CLOSED_NO_FAULT);
        assertThat(response.message()).isEqualTo("Đã từ chối khiếu nại đối chiếu.");
        verify(customerRefundService, never()).refundForDispute(any(), any(), any(), any(), anyString());
        verify(driverWalletRepository, never()).findByDriverIdForUpdate(any());
        verify(auditService).log(eq(manager.getId()), eq(manager.getEmail()), eq("MISMATCH_DISPUTE_REJECTED"),
                eq("DISPUTE"), eq(disputeId.toString()), anyString());
        verify(notificationService).create(eq(customerId), eq(NotificationType.DISPUTE_REJECTED),
                anyString(), anyString());
        verify(notificationService).create(eq(driverId), eq(NotificationType.DISPUTE_REJECTED),
                anyString(), anyString());
    }

    @Test
    void resolveMismatchAcceptFullyCoveredByWalletRefundsDepositPlusPenalty() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = mismatchDispute(disputeId, orderId, customerId, driverId);
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setTotalQuote(new BigDecimal("1000000"));
        order.setCommissionRateSnapshot(new BigDecimal("0.3000"));
        DriverWallet wallet = driverWallet(driverId, "600000");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("800000")), anyString())).thenReturn(new BigDecimal("2000000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolveMismatch(disputeId, manager, true,
                "Da xac minh voi tai xe ve su co nay");

        assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_REFUND);
        assertThat(response.resolutionAmount()).isEqualByComparingTo("800000");
        verify(driverProfileRepository, never()).findByUserId(any());
        verify(userRepository, never()).findById(any());
        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
        verify(notificationService).create(eq(driverId), eq(NotificationType.PENALTY_WALLET_DEDUCTED),
                anyString(), anyString());
    }

    @Test
    void resolveMismatchAcceptWalletPartialDepositCoversRestNoSuspension() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = mismatchDispute(disputeId, orderId, customerId, driverId);
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setTotalQuote(new BigDecimal("1000000"));
        order.setCommissionRateSnapshot(new BigDecimal("0.3000"));
        DriverWallet wallet = driverWallet(driverId, "200000");
        DriverProfile profile = driverProfile(driverId, "300000");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("800000")), anyString())).thenReturn(new BigDecimal("800000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolveMismatch(disputeId, manager, true,
                "Da xac minh voi tai xe ve su co nay");

        assertThat(response.resolutionAmount()).isEqualByComparingTo("800000");
        verify(userRepository, never()).findById(any());
        verify(driverProfileRepository).saveAndFlush(any(DriverProfile.class));
        assertThat(profile.getDepositAmount()).isEqualByComparingTo("0");
    }

    @Test
    void resolveMismatchAcceptWalletAndDepositInsufficientSuspendsDriver() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = mismatchDispute(disputeId, orderId, customerId, driverId);
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setTotalQuote(new BigDecimal("1000000"));
        order.setCommissionRateSnapshot(new BigDecimal("0.3000"));
        DriverWallet wallet = driverWallet(driverId, "0");
        DriverProfile profile = driverProfile(driverId, "100000");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.ACTIVE);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("400000")), anyString())).thenReturn(new BigDecimal("400000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolveMismatch(disputeId, manager, true, "Da xac minh voi tai xe ve su co nay");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(captor.getValue().getSuspensionReason()).contains("Còn nợ 400000 VND");
    }

    @Test
    void resolveMismatchAcceptWithMissingDriverProfileTreatsDepositAsZero() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = mismatchDispute(disputeId, orderId, customerId, driverId);
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        order.setTotalQuote(new BigDecimal("1000000"));
        order.setCommissionRateSnapshot(new BigDecimal("0.3000"));
        DriverWallet wallet = driverWallet(driverId, "0");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.ACTIVE);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("300000")), anyString())).thenReturn(new BigDecimal("300000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resolveMismatch(disputeId, manager, true, "Da xac minh voi tai xe ve su co nay");

        verify(driverProfileRepository, never()).saveAndFlush(any());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSuspensionReason()).contains("Còn nợ 500000 VND");
    }

    // ===================== resolveDeduct() =====================

    @Test
    void resolveDeductWithoutAuthenticatedActorThrowsUnauthorized() {
        assertThatThrownBy(() -> service.resolveDeduct(UUID.randomUUID(), null,
                new ResolveDeductRequest(new BigDecimal("100000"), "Bang chung hop le de khau tru")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void resolveDeductWithInvalidNoteThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        assertThatThrownBy(() -> service.resolveDeduct(UUID.randomUUID(), manager,
                new ResolveDeductRequest(new BigDecimal("100000"), "ngan")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_NOTE|");
                });
    }

    @Test
    void resolveDeductWithNonPositiveAmountThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        assertThatThrownBy(() -> service.resolveDeduct(UUID.randomUUID(), manager,
                new ResolveDeductRequest(BigDecimal.ZERO, "Bang chung hop le de khau tru")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("INVALID_RESOLUTION_AMOUNT|");
                });
    }

    @Test
    void resolveDeductWhenAlreadyPendingThrowsConflict() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        dispute.setPendingDeductShortfall(new BigDecimal("50000"));
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolveDeduct(disputeId, manager,
                new ResolveDeductRequest(new BigDecimal("100000"), "Bang chung hop le de khau tru")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DEDUCTION_ALREADY_PENDING|");
                });
    }

    @Test
    void resolveDeductWithAmountExceedingOrderTotalThrowsUnprocessableEntity() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.resolveDeduct(disputeId, manager,
                new ResolveDeductRequest(new BigDecimal("2000000"), "Bang chung hop le de khau tru")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                    assertThat(ex.getReason()).startsWith("AMOUNT_EXCEEDS_ORDER_TOTAL|");
                });
    }

    @Test
    void resolveDeductWhenDriverWalletMissingThrowsConflict() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(dispute.getDriverId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveDeduct(disputeId, manager,
                new ResolveDeductRequest(new BigDecimal("100000"), "Bang chung hop le de khau tru")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DRIVER_WALLET_NOT_FOUND|");
                });
    }

    @Test
    void resolveDeductFullyCoveredByWalletResolvesAndRefundsCustomer() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        DriverWallet wallet = driverWallet(dispute.getDriverId(), "500000");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(dispute.getDriverId()))
                .thenReturn(Optional.of(wallet));
        when(customerRefundService.refundForDispute(eq(dispute.getCustomerId()), eq(orderId), eq(disputeId),
                eq(new BigDecimal("100000")), anyString())).thenReturn(new BigDecimal("100000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolveDeduct(disputeId, manager,
                new ResolveDeductRequest(new BigDecimal("100000"), "Bang chung hop le de khau tru"));

        assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_DEDUCT);
        assertThat(response.resolutionAmount()).isEqualByComparingTo("100000");
        assertThat(response.orderStatus()).isEqualTo("COMPLETED");
        verify(driverEarningService).creditEarning(order);
        verify(notificationService).create(eq(dispute.getDriverId()), eq(NotificationType.PENALTY_WALLET_DEDUCTED),
                anyString(), anyString());
        verify(auditService).log(eq(manager.getId()), eq(manager.getEmail()), eq("DISPUTE_RESOLVED_DEDUCT"),
                eq("DISPUTE"), eq(disputeId.toString()), anyString());
    }

    @Test
    void resolveDeductWithZeroWalletBalanceCreatesPendingShortfallWithoutRefund() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        DriverWallet wallet = driverWallet(dispute.getDriverId(), "0");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(dispute.getDriverId()))
                .thenReturn(Optional.of(wallet));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolveDeduct(disputeId, manager,
                new ResolveDeductRequest(new BigDecimal("100000"), "Bang chung hop le de khau tru"));

        assertThat(response.status()).isEqualTo(DisputeStatus.INVESTIGATING);
        assertThat(response.message()).contains("chờ tài xế nộp bổ sung trong 5 phút.");
        verify(customerRefundService, never()).refundForDispute(any(), any(), any(), any(), anyString());
        verify(notificationService, never()).create(eq(dispute.getDriverId()),
                eq(NotificationType.PENALTY_WALLET_DEDUCTED), anyString(), anyString());
        verify(notificationService).create(eq(dispute.getDriverId()), eq(NotificationType.PENALTY_TOP_UP_REQUIRED),
                anyString(), anyString());
        verify(auditService).log(eq(manager.getId()), eq(manager.getEmail()), eq("DISPUTE_DEDUCT_PENDING"),
                eq("DISPUTE"), eq(disputeId.toString()), anyString());
        assertThat(dispute.getPendingDeductShortfall()).isEqualByComparingTo("100000");
        assertThat(dispute.getDeductDeadline()).isNotNull();
    }

    @Test
    void resolveDeductWithPartialWalletCreatesPendingShortfallWithPartialRefund() {
        User manager = user(UserRole.MANAGER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, orderId);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), dispute.getDriverId());
        DriverWallet wallet = driverWallet(dispute.getDriverId(), "300000");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(dispute.getDriverId()))
                .thenReturn(Optional.of(wallet));
        when(customerRefundService.refundForDispute(eq(dispute.getCustomerId()), eq(orderId), eq(disputeId),
                eq(new BigDecimal("300000")), anyString())).thenReturn(new BigDecimal("300000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.resolveDeduct(disputeId, manager,
                new ResolveDeductRequest(new BigDecimal("500000"), "Bang chung hop le de khau tru"));

        assertThat(response.status()).isEqualTo(DisputeStatus.INVESTIGATING);
        assertThat(dispute.getPendingDeductShortfall()).isEqualByComparingTo("200000");
        verify(customerRefundService).refundForDispute(eq(dispute.getCustomerId()), eq(orderId), eq(disputeId),
                eq(new BigDecimal("300000")), anyString());
        verify(notificationService).create(eq(dispute.getDriverId()), eq(NotificationType.PENALTY_WALLET_DEDUCTED),
                anyString(), anyString());
        verify(notificationService).create(eq(dispute.getDriverId()), eq(NotificationType.PENALTY_TOP_UP_REQUIRED),
                anyString(), anyString());
    }

    // ===================== getPendingPenalty() =====================

    @Test
    void getPendingPenaltyReturnsNullWhenNoneFound() {
        UUID driverId = UUID.randomUUID();
        when(disputeRepository.findFirstByDriverIdAndPendingDeductShortfallIsNotNullOrderByDeductDeadlineAsc(driverId))
                .thenReturn(Optional.empty());

        assertThat(service.getPendingPenalty(driverId)).isNull();
    }

    @Test
    void getPendingPenaltyReturnsDetailsWithOrderCode() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3);
        Dispute dispute = pendingDeductDispute(disputeId, orderId, UUID.randomUUID(), driverId,
                new BigDecimal("200000"), deadline);
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), driverId);

        when(disputeRepository.findFirstByDriverIdAndPendingDeductShortfallIsNotNullOrderByDeductDeadlineAsc(driverId))
                .thenReturn(Optional.of(dispute));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DriverPenaltyResponse response = service.getPendingPenalty(driverId);

        assertThat(response.disputeId()).isEqualTo(disputeId);
        assertThat(response.orderCode()).isEqualTo(order.getOrderCode());
        assertThat(response.shortfall()).isEqualByComparingTo("200000");
        assertThat(response.deadline()).isEqualTo(deadline);
    }

    @Test
    void getPendingPenaltyReturnsNullOrderCodeWhenOrderMissing() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(UUID.randomUUID(), orderId, UUID.randomUUID(), driverId,
                new BigDecimal("200000"), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3));

        when(disputeRepository.findFirstByDriverIdAndPendingDeductShortfallIsNotNullOrderByDeductDeadlineAsc(driverId))
                .thenReturn(Optional.of(dispute));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        DriverPenaltyResponse response = service.getPendingPenalty(driverId);

        assertThat(response.orderCode()).isNull();
    }

    // ===================== payPenaltyMock() =====================

    @Test
    void payPenaltyMockWithoutAuthenticatedDriverThrowsUnauthorized() {
        assertThatThrownBy(() -> service.payPenaltyMock(null, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void payPenaltyMockWhenDisputeNotFoundThrowsNotFound() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payPenaltyMock(driver, disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("DISPUTE_NOT_FOUND|");
                });
    }

    @Test
    void payPenaltyMockWhenNotOwningDriverThrowsForbidden() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3));
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.payPenaltyMock(driver, disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(403);
                    assertThat(ex.getReason()).startsWith("FORBIDDEN|");
                });
    }

    @Test
    void payPenaltyMockWhenNoPendingPenaltyThrowsConflict() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, UUID.randomUUID(), UUID.randomUUID(), driver.getId(),
                "DAMAGE", DisputeStatus.OPEN);
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.payPenaltyMock(driver, disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("NO_PENDING_PENALTY|");
                });
    }

    @Test
    void payPenaltyMockWhenPastDeadlineThrowsConflict() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, UUID.randomUUID(), UUID.randomUUID(), driver.getId(),
                new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.payPenaltyMock(driver, disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("PENALTY_EXPIRED|");
                });
    }

    @Test
    void payPenaltyMockWhenOrderNotFoundThrowsNotFound() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, UUID.randomUUID(), driver.getId(),
                new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3));
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payPenaltyMock(driver, disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                    assertThat(ex.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    @Test
    void payPenaltyMockWhenDriverWalletMissingThrowsConflict() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, UUID.randomUUID(), driver.getId(),
                new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3));
        ServiceOrder order = disputedOrder(orderId, dispute.getCustomerId(), driver.getId());

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driver.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payPenaltyMock(driver, disputeId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).startsWith("DRIVER_WALLET_NOT_FOUND|");
                });
    }

    @Test
    void payPenaltyMockWithNullDeadlineSettlesShortfallAndAddsToExistingResolutionAmount() {
        User driver = user(UserRole.DRIVER);
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driver.getId(),
                new BigDecimal("200000"), null);
        dispute.setResolutionAmount(new BigDecimal("300000"));
        ServiceOrder order = disputedOrder(orderId, customerId, driver.getId());
        DriverWallet wallet = driverWallet(driver.getId(), "0");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driver.getId())).thenReturn(Optional.of(wallet));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeActionResponse response = service.payPenaltyMock(driver, disputeId);

        assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED_DEDUCT);
        assertThat(response.resolutionAmount()).isEqualByComparingTo("500000");
        verify(customerRefundService).refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("200000")), anyString());
        verify(notificationService).create(eq(driver.getId()), eq(NotificationType.PENALTY_SETTLED),
                anyString(), anyString());
        verify(notificationService).create(eq(customerId), eq(NotificationType.DISPUTE_RESOLVED),
                anyString(), anyString());
        verify(orderRepository).save(order);
        assertThat(dispute.getPendingDeductShortfall()).isNull();
        assertThat(dispute.getDeductDeadline()).isNull();
    }

    // ===================== enforcePenalty() =====================

    @Test
    void enforcePenaltyDoesNothingWhenDisputeMissing() {
        UUID disputeId = UUID.randomUUID();
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.empty());

        service.enforcePenalty(disputeId);

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void enforcePenaltyDoesNothingWhenNoPendingShortfall() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = openDispute(disputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "DAMAGE", DisputeStatus.OPEN);
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        service.enforcePenalty(disputeId);

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void enforcePenaltyDoesNothingWhenDeadlineNotYetPassed() {
        UUID disputeId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3));
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));

        service.enforcePenalty(disputeId);

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void enforcePenaltyDoesNothingWhenOrderMissing() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        service.enforcePenalty(disputeId);

        verify(driverWalletRepository, never()).findByDriverIdForUpdate(any());
    }

    @Test
    void enforcePenaltyProceedsWhenDeadlineIsNullAndWalletFullyCovers() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("100000"), null);
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "500000");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("100000")), anyString())).thenReturn(new BigDecimal("100000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        verify(driverProfileRepository, never()).findByUserId(any());
        verify(userRepository, never()).findById(any());
        verify(orderRepository).save(order);
        verify(auditService).log(isNull(), eq("SYSTEM"), eq("DISPUTE_PENALTY_ENFORCED"), eq("DISPUTE"),
                eq(disputeId.toString()), anyString());
        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_DEDUCT);
        assertThat(dispute.getPendingDeductShortfall()).isNull();
    }

    @Test
    void enforcePenaltyDepositFullyCoversRemainingButStillSuspendsDriver() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("500000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "0");
        DriverProfile profile = driverProfile(driverId, "500000");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.ACTIVE);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("500000")), anyString())).thenReturn(new BigDecimal("500000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(captor.getValue().getSuspensionReason()).doesNotContain("Còn nợ");
    }

    @Test
    void enforcePenaltyDepositPartiallyCoversRemainingSuspendsWithDebtNote() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("500000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "0");
        DriverProfile profile = driverProfile(driverId, "100000");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.ACTIVE);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("100000")), anyString())).thenReturn(new BigDecimal("100000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSuspensionReason()).contains("Còn nợ 400000 VND");
    }

    @Test
    void enforcePenaltySkipsSuspendWhenDriverAlreadySuspended() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("500000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "0");
        DriverProfile profile = driverProfile(driverId, "100000");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.SUSPENDED);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("100000")), anyString())).thenReturn(new BigDecimal("100000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void enforcePenaltySkipsSuspendWhenDriverNotFound() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("500000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "0");
        DriverProfile profile = driverProfile(driverId, "100000");

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());
        when(customerRefundService.refundForDispute(eq(customerId), eq(orderId), eq(disputeId),
                eq(new BigDecimal("100000")), anyString())).thenReturn(new BigDecimal("100000"));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void enforcePenaltyWithZeroDriverDepositDoesNotPersistProfileButStillAttemptsSuspend() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("500000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "0");
        DriverProfile profile = driverProfile(driverId, "0");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.ACTIVE);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        verify(driverProfileRepository, never()).saveAndFlush(any());
        verify(customerRefundService, never()).refundForDispute(any(), any(), any(), any(), anyString());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void enforcePenaltySkipsRefundWhenNothingCollectedFromMissingProfile() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Dispute dispute = pendingDeductDispute(disputeId, orderId, customerId, driverId,
                new BigDecimal("500000"), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ServiceOrder order = disputedOrder(orderId, customerId, driverId);
        DriverWallet wallet = driverWallet(driverId, "0");
        User driver = user(UserRole.DRIVER);
        driver.setId(driverId);
        driver.setStatus(UserStatus.ACTIVE);

        when(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(disputeRepository.saveAndFlush(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enforcePenalty(disputeId);

        verify(customerRefundService, never()).refundForDispute(any(), any(), any(), any(), anyString());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSuspensionReason()).contains("Còn nợ 500000 VND");
    }

    // ===================== findExpiredPenaltyIds() / defaultPageSize() =====================

    @Test
    void findExpiredPenaltyIdsDelegatesToRepositoryWithCurrentTime() {
        UUID expiredId = UUID.randomUUID();
        when(disputeRepository.findExpiredPendingDeductionIds(any(OffsetDateTime.class)))
                .thenReturn(List.of(expiredId));

        List<UUID> result = service.findExpiredPenaltyIds();

        assertThat(result).containsExactly(expiredId);
        verify(disputeRepository).findExpiredPendingDeductionIds(any(OffsetDateTime.class));
    }

    @Test
    void defaultPageSizeReturnsConfiguredConstant() {
        assertThat(service.defaultPageSize()).isEqualTo(20);
    }

    // ===================== Helpers bo sung =====================

    private User userWithId(UUID id, UserRole role) {
        User u = user(role);
        u.setId(id);
        return u;
    }

    private Dispute openDispute(UUID disputeId, UUID orderId, UUID customerId, UUID driverId,
                                 String claimType, String status) {
        return Dispute.builder()
                .id(disputeId)
                .orderId(orderId)
                .customerId(customerId)
                .driverId(driverId)
                .claimType(claimType)
                .claimAmount(new BigDecimal("500000"))
                .customerStatement("Tu go bi tray xuoc sau khi van chuyen")
                .status(status)
                .deadline(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3))
                .build();
    }

    private Dispute mismatchDispute(UUID disputeId, UUID orderId, UUID customerId, UUID driverId) {
        return openDispute(disputeId, orderId, customerId, driverId, "DRIVER_MISMATCH", DisputeStatus.OPEN);
    }

    private Dispute pendingDeductDispute(UUID disputeId, UUID orderId, UUID customerId, UUID driverId,
                                          BigDecimal shortfall, OffsetDateTime deadline) {
        Dispute dispute = openDispute(disputeId, orderId, customerId, driverId, "DAMAGE",
                DisputeStatus.INVESTIGATING);
        dispute.setPendingDeductShortfall(shortfall);
        dispute.setDeductDeadline(deadline);
        return dispute;
    }

    private DriverWallet driverWallet(UUID driverId, String balance) {
        return DriverWallet.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .balance(new BigDecimal(balance))
                .build();
    }

    private DriverProfile driverProfile(UUID driverId, String deposit) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(driverId)
                .depositAmount(new BigDecimal(deposit))
                .build();
    }

    private User user(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@movehome.vn")
                .fullName(role.name() + " User")
                .phone("+84901234567")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private ServiceOrder completedOrder(UUID orderId, UUID customerId, UUID driverId) {
        return ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH202606260001")
                .customerId(customerId)
                .driverId(driverId)
                .pickupAddress("Pickup")
                .dropoffAddress("Dropoff")
                .scheduledAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
                .vehicleType("TRUCK_500KG")
                .build();
    }

    private ServiceOrder disputedOrder(UUID orderId, UUID customerId, UUID driverId) {
        ServiceOrder order = completedOrder(orderId, customerId, driverId);
        order.setStatus("DISPUTED");
        return order;
    }

    private Dispute openDispute(UUID disputeId, UUID orderId) {
        return Dispute.builder()
                .id(disputeId)
                .orderId(orderId)
                .customerId(UUID.randomUUID())
                .driverId(UUID.randomUUID())
                .claimType("DAMAGE")
                .claimAmount(new BigDecimal("500000"))
                .customerStatement("Tu go bi tray xuoc sau khi van chuyen")
                .status(DisputeStatus.OPEN)
                .deadline(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3))
                .build();
    }
}
