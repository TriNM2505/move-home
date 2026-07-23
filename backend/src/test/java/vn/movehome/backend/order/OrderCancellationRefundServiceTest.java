package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationRefundServiceTest {

    @Mock
    private OrderCancellationRefundRepository refundRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private OrderCancellationRefundService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationRefundService(refundRepository, userRepository, notificationService);
    }

    private ServiceOrder order(UUID id, String code) {
        return ServiceOrder.builder()
                .id(id)
                .orderCode(code)
                .customerId(UUID.randomUUID())
                .pickupAddress("123 Duong Lang, Dong Da, Ha Noi")
                .dropoffAddress("456 Cau Giay, Ha Noi")
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .totalQuote(new BigDecimal("1000000"))
                .build();
    }

    @Test
    void openForCancelledOrderSkipsWhenRefundAlreadyExistsForOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(orderId, "MH0001");
        when(refundRepository.existsByOrderId(orderId)).thenReturn(true);

        service.openForCancelledOrder(order, customerId, "Doi y khong chuyen nua");

        verify(refundRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userRepository, notificationService);
    }

    @Test
    void openForCancelledOrderCreatesPendingRefundAndNotifiesActiveManagers() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(orderId, "MH0002");
        User manager1 = User.builder().id(UUID.randomUUID()).build();
        User manager2 = User.builder().id(UUID.randomUUID()).build();

        when(refundRepository.existsByOrderId(orderId)).thenReturn(false);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(manager1, manager2));

        service.openForCancelledOrder(order, customerId, "Doi y khong chuyen nua");

        ArgumentCaptor<OrderCancellationRefund> captor = ArgumentCaptor.forClass(OrderCancellationRefund.class);
        verify(refundRepository).saveAndFlush(captor.capture());
        OrderCancellationRefund saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getReason()).isEqualTo("Doi y khong chuyen nua");
        assertThat(saved.getStatus()).isEqualTo(OrderCancellationRefund.STATUS_PENDING);

        verify(notificationService).create(eq(manager1.getId()), eq(NotificationType.ORDER_CANCELLED),
                eq("Yêu cầu hoàn cọc do hủy đơn"),
                eq("Đơn MH0002 bị khách hủy khi chưa có tài xế — cần xem xét hoàn cọc."));
        verify(notificationService).create(eq(manager2.getId()), eq(NotificationType.ORDER_CANCELLED),
                eq("Yêu cầu hoàn cọc do hủy đơn"),
                eq("Đơn MH0002 bị khách hủy khi chưa có tài xế — cần xem xét hoàn cọc."));
        verify(notificationService, times(2)).create(any(), any(), any(), any());
    }

    @Test
    void openForCancelledOrderSkipsNotifyForManagerWithNullId() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(orderId, "MH0003");
        User managerWithoutId = User.builder().id(null).build();

        when(refundRepository.existsByOrderId(orderId)).thenReturn(false);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(managerWithoutId));

        service.openForCancelledOrder(order, customerId, "Doi y khong chuyen nua");

        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void openForCancelledOrderSwallowsNotificationFailureForEachManager() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(orderId, "MH0004");
        User manager = User.builder().id(UUID.randomUUID()).build();

        when(refundRepository.existsByOrderId(orderId)).thenReturn(false);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(manager));
        when(notificationService.create(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        // Khong duoc nem loi ra ngoai — best-effort notify.
        service.openForCancelledOrder(order, customerId, "Doi y khong chuyen nua");

        verify(refundRepository).saveAndFlush(any(OrderCancellationRefund.class));
        verify(notificationService).create(eq(manager.getId()), any(), any(), any());
    }

    @Test
    void openForCancelledOrderNotifiesNoOneWhenNoActiveManagers() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(orderId, "MH0005");

        when(refundRepository.existsByOrderId(orderId)).thenReturn(false);
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of());

        service.openForCancelledOrder(order, customerId, "Doi y khong chuyen nua");

        verify(refundRepository).saveAndFlush(any(OrderCancellationRefund.class));
        verifyNoInteractions(notificationService);
    }
}
