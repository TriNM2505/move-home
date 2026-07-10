package vn.movehome.backend.dispute;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
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
import static org.mockito.Mockito.never;
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
