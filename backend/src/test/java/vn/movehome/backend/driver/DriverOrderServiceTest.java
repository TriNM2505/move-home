package vn.movehome.backend.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.incident.DriverIncidentService;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.service.NotificationService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DriverOrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderStatusTransitionService orderStatusTransitionService = mock(OrderStatusTransitionService.class);
    private final DriverEarningService driverEarningService = mock(DriverEarningService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final DriverIncidentService driverIncidentService = mock(DriverIncidentService.class);

    private DriverOrderService service;

    private final UUID driverId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DriverOrderService(
                orderRepository,
                orderStatusTransitionService,
                driverEarningService,
                notificationService,
                driverIncidentService);
        ReflectionTestUtils.setField(service, "noShowMinutes", 3L);
    }

    private ServiceOrder.ServiceOrderBuilder baseOrder() {
        return ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-0001")
                .customerId(customerId);
    }

    // ================= acceptOrder =================

    @Test
    void acceptOrder_happyPath_setsDriverAndTransitions() {
        ServiceOrder order = baseOrder().status("CONFIRMED").build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.existsByDriverIdAndStatusInAndDeletedAtIsNull(eq(driverId), any()))
                .thenReturn(false);

        service.acceptOrder(driverId, "DRIVER", orderId);

        assertThat(order.getDriverId()).isEqualTo(driverId);
        verify(orderStatusTransitionService).transition(
                eq(order), eq("ACCEPTED"), eq(driverId), eq("DRIVER"), any());
        verify(driverIncidentService).resolveReassigned(orderId);
    }

    @Test
    void acceptOrder_throwsWhenOrderNotConfirmed() {
        ServiceOrder order = baseOrder().status("ASSIGNED").build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.acceptOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Đơn không còn khả dụng");
        verify(orderStatusTransitionService, never()).transition(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void acceptOrder_throwsWhenOrderAlreadyHasDriver() {
        ServiceOrder order = baseOrder().status("CONFIRMED").driverId(UUID.randomUUID()).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.acceptOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Đơn không còn khả dụng");
    }

    @Test
    void acceptOrder_throwsWhenDriverAlreadyBusy() {
        ServiceOrder order = baseOrder().status("CONFIRMED").build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.existsByDriverIdAndStatusInAndDeletedAtIsNull(eq(driverId), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.acceptOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bạn đang có đơn chưa hoàn thành. Hãy hoàn thành đơn đó trước khi nhận đơn mới.");
        verify(orderStatusTransitionService, never()).transition(any(), anyString(), any(), anyString(), any());
    }

    // ================= findForUpdate (common) =================

    @Test
    void acceptOrder_throwsNotFoundWhenOrderMissing() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
    }

    @Test
    void acceptOrder_throwsNotFoundWhenOrderSoftDeleted() {
        ServiceOrder order = baseOrder().status("CONFIRMED").deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.acceptOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    // ================= requireOwnership (common) =================

    @Test
    void markArrived_throwsAccessDeniedWhenNotOwner() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(UUID.randomUUID()).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.markArrived(driverId, "DRIVER", orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn chỉ có thể thao tác đơn hàng của chính mình.");
    }

    // ================= markArrived =================

    @Test
    void markArrived_throwsWhenNotAccepted() {
        ServiceOrder order = baseOrder().status("IN_PROGRESS").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.markArrived(driverId, "DRIVER", orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_STATE|Chỉ đánh dấu 'đã đến điểm đón' khi đơn đang ở trạng thái đã nhận.");
    }

    @Test
    void markArrived_idempotent_whenAlreadyArrived() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId)
                .arrivedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.markArrived(driverId, "DRIVER", orderId);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void markArrived_happyPath_setsArrivedAtAndNotifiesCustomer() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.markArrived(driverId, "DRIVER", orderId);

        assertThat(order.getArrivedAt()).isNotNull();
        verify(orderRepository).save(order);
        verify(notificationService).create(
                eq(customerId),
                eq("DRIVER_ARRIVED"),
                eq("Tài xế đã đến điểm đón"),
                eq("Tài xế đơn " + order.getOrderCode()
                        + " đã đến điểm đón. Vui lòng đối chiếu tài xế/xe với ảnh rồi xác nhận."));
    }

    @Test
    void markArrived_skipsNotificationWhenCustomerIdNull() {
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId).orderCode("MH-0002").customerId(null)
                .status("ACCEPTED").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.markArrived(driverId, "DRIVER", orderId);

        verifyNoInteractions(notificationService);
    }

    @Test
    void markArrived_swallowsNotificationException() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(notificationService.create(any(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        // Khong duoc nem loi ra ngoai du notification that bai
        service.markArrived(driverId, "DRIVER", orderId);

        assertThat(order.getArrivedAt()).isNotNull();
    }

    // ================= cancelNoShow =================

    @Test
    void cancelNoShow_throwsWhenNotAccepted() {
        ServiceOrder order = baseOrder().status("IN_PROGRESS").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelNoShow(driverId, "DRIVER", orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_STATE|Chỉ hủy do khách vắng khi đơn đang ở trạng thái đã nhận.");
    }

    @Test
    void cancelNoShow_throwsWhenNotArrivedYet() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelNoShow(driverId, "DRIVER", orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NOT_ARRIVED|Bạn cần bấm 'Đã đến điểm đón' trước.");
    }

    @Test
    void cancelNoShow_throwsWhenTooEarly() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId)
                .arrivedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelNoShow(driverId, "DRIVER", orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NO_SHOW_TOO_EARLY|Vui lòng chờ đủ 3 phút sau khi đến điểm đón mới được hủy.");
        verify(driverEarningService, never()).creditNoShowCompensation(any());
    }

    @Test
    void cancelNoShow_happyPath_creditsCompensationAndCancelsOrder() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId)
                .arrivedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.cancelNoShow(driverId, "DRIVER", orderId);

        verify(driverEarningService).creditNoShowCompensation(order);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getCancellationReason()).isEqualTo("Khách không có mặt tại điểm đón (no-show).");
        verify(orderStatusTransitionService).transition(
                eq(order), eq("CANCELLED"), eq(driverId), eq("DRIVER"), any());
        verify(notificationService).create(
                eq(customerId),
                eq("ORDER_CANCELLED"),
                eq("Đơn đã hủy do bạn vắng mặt"),
                eq("Đơn " + order.getOrderCode()
                        + " đã bị hủy do bạn không có mặt tại điểm đón. Tiền cọc thuộc về tài xế theo chính sách."));
    }

    // ================= requestFinalPayment =================

    @Test
    void requestFinalPayment_throwsWhenNotInProgress() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.requestFinalPayment(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chỉ có thể yêu cầu thanh toán khi đơn đang được thực hiện");
    }

    @Test
    void requestFinalPayment_happyPath_transitionsToAwaitingFinalPayment() {
        ServiceOrder order = baseOrder().status("IN_PROGRESS").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.requestFinalPayment(driverId, "DRIVER", orderId);

        verify(orderStatusTransitionService).transition(
                eq(order), eq("AWAITING_FINAL_PAYMENT"), eq(driverId), eq("DRIVER"), any());
    }

    // ================= completeOrder =================

    @Test
    void completeOrder_throwsWhenNotAwaitingFinalPayment() {
        ServiceOrder order = baseOrder().status("IN_PROGRESS").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cần yêu cầu khách thanh toán 70% trước khi hoàn thành");
    }

    @Test
    void completeOrder_throwsWhenFinalNotPaid() {
        ServiceOrder order = baseOrder().status("AWAITING_FINAL_PAYMENT").driverId(driverId).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Khách chưa thanh toán nốt 70%, chưa thể hoàn thành đơn");
    }

    @Test
    void completeOrder_happyPath_setsCompletedAtAndTransitions() {
        ServiceOrder order = baseOrder().status("AWAITING_FINAL_PAYMENT").driverId(driverId)
                .finalPaidAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        service.completeOrder(driverId, "DRIVER", orderId);

        assertThat(order.getCompletedAt()).isNotNull();
        verify(orderStatusTransitionService).transition(
                eq(order), eq("COMPLETED"), eq(driverId), eq("DRIVER"), any());
    }

    @Test
    void completeOrder_throwsAccessDeniedWhenNotOwner() {
        ServiceOrder order = baseOrder().status("AWAITING_FINAL_PAYMENT").driverId(UUID.randomUUID())
                .finalPaidAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
