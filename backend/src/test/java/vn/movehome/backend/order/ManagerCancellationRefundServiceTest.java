package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.CustomerRefundService;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerCancellationRefundServiceTest {

    @Mock
    private OrderCancellationRefundRepository refundRepository;

    @Mock
    private OrderCancellationPhotoService photoService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRefundService customerRefundService;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    private ManagerCancellationRefundService service;

    @BeforeEach
    void setUp() {
        service = new ManagerCancellationRefundService(
                refundRepository, photoService, orderRepository, userRepository,
                customerRefundService, auditService, notificationService);
    }

    private ServiceOrder order(UUID id, UUID customerId, String code, BigDecimal totalQuote, BigDecimal commissionRate) {
        return ServiceOrder.builder()
                .id(id)
                .orderCode(code)
                .customerId(customerId)
                .pickupAddress("123 Duong Lang, Dong Da, Ha Noi")
                .dropoffAddress("456 Cau Giay, Ha Noi")
                .scheduledAt(OffsetDateTime.now().plusDays(1))
                .totalQuote(totalQuote)
                .commissionRateSnapshot(commissionRate)
                .build();
    }

    private OrderCancellationRefund pendingRow(UUID id, UUID orderId, UUID customerId) {
        return OrderCancellationRefund.builder()
                .id(id)
                .orderId(orderId)
                .customerId(customerId)
                .reason("Doi y khong chuyen nua")
                .status(OrderCancellationRefund.STATUS_PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private User user(UUID id, String name, String phone) {
        return User.builder().id(id).fullName(name).phone(phone).build();
    }

    // ===================== list =====================

    @Test
    void listUsesFindAllWhenStatusIsNull() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(UUID.randomUUID(), orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0001", new BigDecimal("1000000"), new BigDecimal("0.3000"));
        User customer = user(customerId, "Nguyen Van A", "+84912345678");
        Page<OrderCancellationRefund> page = new PageImpl<>(List.of(row));

        when(refundRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);
        when(orderRepository.findAllById(List.of(orderId))).thenReturn(List.of(order));
        when(userRepository.findAllById(List.of(customerId))).thenReturn(List.of(customer));

        Page<CancellationRefundListItem> result = service.list(null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        CancellationRefundListItem item = result.getContent().get(0);
        assertThat(item.orderCode()).isEqualTo("MH0001");
        assertThat(item.customerName()).isEqualTo("Nguyen Van A");
        assertThat(item.depositAmount()).isEqualByComparingTo("300000");
        assertThat(item.status()).isEqualTo(OrderCancellationRefund.STATUS_PENDING);
        verify(refundRepository).findAllByOrderByCreatedAtDesc(any(PageRequest.class));
        verify(refundRepository, never()).findByStatusOrderByCreatedAtAsc(anyString(), any());
    }

    @Test
    void listTreatsAllStatusCaseInsensitivelyAsNoFilter() {
        Page<OrderCancellationRefund> page = new PageImpl<>(List.of());
        when(refundRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

        Page<CancellationRefundListItem> result = service.list("all", 0, 20);

        assertThat(result.getContent()).isEmpty();
        verify(refundRepository).findAllByOrderByCreatedAtDesc(any(PageRequest.class));
        verify(orderRepository, never()).findAllById(any());
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void listUsesAscendingOrderForPendingStatus() {
        Page<OrderCancellationRefund> page = new PageImpl<>(List.of());
        when(refundRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(page);

        service.list("pending", 0, 20);

        verify(refundRepository).findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(PageRequest.class));
    }

    @Test
    void listUsesDescendingOrderForRejectedStatus() {
        Page<OrderCancellationRefund> page = new PageImpl<>(List.of());
        when(refundRepository.findByStatusOrderByCreatedAtDesc(eq("REJECTED"), any(PageRequest.class)))
                .thenReturn(page);

        service.list("REJECTED", 0, 20);

        verify(refundRepository).findByStatusOrderByCreatedAtDesc(eq("REJECTED"), any(PageRequest.class));
    }

    @Test
    void listUsesDescendingOrderForRefundedStatus() {
        Page<OrderCancellationRefund> page = new PageImpl<>(List.of());
        when(refundRepository.findByStatusOrderByCreatedAtDesc(eq("REFUNDED"), any(PageRequest.class)))
                .thenReturn(page);

        service.list("REFUNDED", 0, 20);

        verify(refundRepository).findByStatusOrderByCreatedAtDesc(eq("REFUNDED"), any(PageRequest.class));
    }

    @Test
    void listRejectsInvalidStatus() {
        assertThatThrownBy(() -> service.list("BOGUS", 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "VALIDATION_ERROR|Trạng thái yêu cầu hoàn cọc không hợp lệ.");
                });
    }

    @Test
    void listRejectsNegativePage() {
        assertThatThrownBy(() -> service.list(null, -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số trang không hợp lệ.");
                });
    }

    @Test
    void listRejectsZeroSize() {
        assertThatThrownBy(() -> service.list(null, 0, 0))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
                });
    }

    @Test
    void listRejectsSizeAboveMax() {
        assertThatThrownBy(() -> service.list(null, 0, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
                });
    }

    @Test
    void listMapsMissingOrderAndCustomerToNullFields() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(UUID.randomUUID(), orderId, customerId);
        Page<OrderCancellationRefund> page = new PageImpl<>(List.of(row));

        when(refundRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);
        when(orderRepository.findAllById(List.of(orderId))).thenReturn(List.of());
        when(userRepository.findAllById(List.of(customerId))).thenReturn(List.of());

        Page<CancellationRefundListItem> result = service.list(null, 0, 20);

        CancellationRefundListItem item = result.getContent().get(0);
        assertThat(item.orderCode()).isNull();
        assertThat(item.customerName()).isNull();
        assertThat(item.depositAmount()).isNull();
    }

    // ===================== detail =====================

    @Test
    void detailReturnsFullResponseWhenOrderAndCustomerExist() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0010", new BigDecimal("2000000"), new BigDecimal("0.3000"));
        order.setStatus("CANCELLED");
        User customer = user(customerId, "Tran Thi B", "+84987654321");

        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(photoService.signedUrls(id)).thenReturn(List.of("https://example.com/1.jpg"));

        CancellationRefundDetailResponse response = service.detail(id);

        assertThat(response.orderCode()).isEqualTo("MH0010");
        assertThat(response.orderStatus()).isEqualTo("CANCELLED");
        assertThat(response.customerName()).isEqualTo("Tran Thi B");
        assertThat(response.customerPhone()).isEqualTo("+84987654321");
        assertThat(response.depositAmount()).isEqualByComparingTo("600000");
        assertThat(response.photoUrls()).containsExactly("https://example.com/1.jpg");
        assertThat(response.order()).isNotNull();
        assertThat(response.order().pickupAddress()).isEqualTo(order.getPickupAddress());
        assertThat(response.order().dropoffAddress()).isEqualTo(order.getDropoffAddress());
        assertThat(response.order().totalQuote()).isEqualByComparingTo("2000000");
    }

    @Test
    void detailUsesDefaultCommissionRateWhenSnapshotIsNull() {
        // depositOf(): commissionRateSnapshot null -> dung DEFAULT_COMMISSION_RATE (0.3000)
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0011", new BigDecimal("2000000"), null);
        User customer = user(customerId, "Tran Thi B", "+84987654321");

        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(photoService.signedUrls(id)).thenReturn(List.of());

        CancellationRefundDetailResponse response = service.detail(id);

        assertThat(response.depositAmount()).isEqualByComparingTo("600000");
    }

    @Test
    void detailReturnsNullOrderFieldsWhenOrderMissing() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);

        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        when(photoService.signedUrls(id)).thenReturn(List.of());

        CancellationRefundDetailResponse response = service.detail(id);

        assertThat(response.orderCode()).isNull();
        assertThat(response.orderStatus()).isNull();
        assertThat(response.depositAmount()).isNull();
        assertThat(response.customerName()).isNull();
        assertThat(response.customerPhone()).isNull();
        assertThat(response.order()).isNull();
    }

    @Test
    void detailThrowsNotFoundWhenRowMissing() {
        UUID id = UUID.randomUUID();
        when(refundRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_NOT_FOUND|Không tìm thấy yêu cầu hoàn cọc.");
                });
    }

    // ===================== refund =====================

    @Test
    void refundCreditsDepositAndMarksRowRefunded() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0020", new BigDecimal("1000000"), new BigDecimal("0.3000"));
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        // detail() re-fetch
        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        when(photoService.signedUrls(id)).thenReturn(List.of());

        CancellationRefundDetailResponse response = service.refund(id, actor);

        verify(customerRefundService).refundForCancellation(
                eq(customerId), eq(orderId), eq(new BigDecimal("300000")), eq("Hoàn cọc do hủy đơn MH0020"));
        assertThat(row.getStatus()).isEqualTo(OrderCancellationRefund.STATUS_REFUNDED);
        assertThat(row.getRefundAmount()).isEqualByComparingTo("300000");
        assertThat(row.getProcessedBy()).isEqualTo(actor.getId());
        assertThat(row.getProcessedAt()).isNotNull();
        verify(refundRepository).saveAndFlush(row);

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(actor.getId()), eq(actor.getEmail()), eq("CANCELLATION_REFUNDED"),
                eq("ORDER_CANCELLATION_REFUND"), eq(id.toString()), detailCaptor.capture());
        assertThat(detailCaptor.getValue()).contains("\"order_code\":\"MH0020\"").contains("\"refund_amount\":300000");

        verify(notificationService).create(eq(customerId), eq(NotificationType.ORDER_CANCELLED),
                eq("Đã hoàn cọc đơn đã hủy"),
                eq("Đơn MH0020: bạn đã được hoàn 300000 VND vào ví."));

        assertThat(response).isNotNull();
    }

    @Test
    void refundThrowsNotFoundWhenRequestDoesNotExist() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();
        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refund(id, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_NOT_FOUND|Không tìm thấy yêu cầu hoàn cọc.");
                });
        verify(customerRefundService, never()).refundForCancellation(any(), any(), any(), any());
    }

    @Test
    void refundThrowsConflictWhenRequestAlreadyProcessed() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        row.setStatus(OrderCancellationRefund.STATUS_REJECTED);
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.refund(id, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_ALREADY_PROCESSED|Yêu cầu hoàn cọc đã được xử lý.");
                });
        verify(customerRefundService, never()).refundForCancellation(any(), any(), any(), any());
    }

    @Test
    void refundThrowsNotFoundWhenOrderMissing() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refund(id, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
                });
    }

    @Test
    void refundThrowsUnprocessableWhenDepositIsZero() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0030", BigDecimal.ZERO, new BigDecimal("0.3000"));
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.refund(id, actor))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "NO_DEPOSIT_TO_REFUND|Đơn không có tiền cọc để hoàn.");
                });
        verify(customerRefundService, never()).refundForCancellation(any(), any(), any(), any());
    }

    @Test
    void refundSkipsNotifyWhenCustomerIdIsNull() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, null);
        ServiceOrder order = order(orderId, null, "MH0040", new BigDecimal("1000000"), new BigDecimal("0.3000"));
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(photoService.signedUrls(id)).thenReturn(List.of());

        service.refund(id, actor);

        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void refundSwallowsNotificationFailureAndStillReturnsDetail() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0050", new BigDecimal("1000000"), new BigDecimal("0.3000"));
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        when(photoService.signedUrls(id)).thenReturn(List.of());
        when(notificationService.create(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        CancellationRefundDetailResponse response = service.refund(id, actor);

        assertThat(response).isNotNull();
        assertThat(row.getStatus()).isEqualTo(OrderCancellationRefund.STATUS_REFUNDED);
    }

    // ===================== reject =====================

    @Test
    void rejectMarksRowRejectedWithReasonAndNotifiesCustomer() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        ServiceOrder order = order(orderId, customerId, "MH0060", new BigDecimal("1000000"), new BigDecimal("0.3000"));
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        when(photoService.signedUrls(id)).thenReturn(List.of());

        CancellationRefundDetailResponse response = service.reject(id, actor, "  Khong du dieu kien hoan coc  ");

        assertThat(row.getStatus()).isEqualTo(OrderCancellationRefund.STATUS_REJECTED);
        assertThat(row.getRejectionReason()).isEqualTo("Khong du dieu kien hoan coc");
        assertThat(row.getProcessedBy()).isEqualTo(actor.getId());
        assertThat(row.getProcessedAt()).isNotNull();
        verify(refundRepository).saveAndFlush(row);
        verify(auditService).log(eq(actor.getId()), eq(actor.getEmail()), eq("CANCELLATION_REFUND_REJECTED"),
                eq("ORDER_CANCELLATION_REFUND"), eq(id.toString()), eq("{\"order_code\":\"MH0060\"}"));
        verify(notificationService).create(eq(customerId), eq(NotificationType.ORDER_CANCELLED),
                eq("Yêu cầu hoàn cọc bị từ chối"),
                eq("Đơn MH0060: yêu cầu hoàn cọc không được chấp nhận. Lý do: Khong du dieu kien hoan coc"));
        assertThat(response).isNotNull();
    }

    @Test
    void rejectUsesOrderIdAsFallbackWhenOrderMissing() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
        when(refundRepository.findById(id)).thenReturn(Optional.of(row));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        when(photoService.signedUrls(id)).thenReturn(List.of());

        service.reject(id, actor, "Ly do khong hop le");

        verify(auditService).log(any(), any(), eq("CANCELLATION_REFUND_REJECTED"), any(),
                eq(id.toString()), eq("{\"order_code\":\"" + orderId + "\"}"));
    }

    @Test
    void rejectThrowsWhenReasonIsNull() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        assertThatThrownBy(() -> service.reject(id, actor, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "VALIDATION_ERROR|Lý do từ chối phải từ 3 đến 500 ký tự.");
                });
        verify(refundRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectThrowsWhenReasonTooShort() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        assertThatThrownBy(() -> service.reject(id, actor, " a "))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "VALIDATION_ERROR|Lý do từ chối phải từ 3 đến 500 ký tự.");
                });
    }

    @Test
    void rejectThrowsWhenReasonTooLong() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();
        String longReason = "a".repeat(501);

        assertThatThrownBy(() -> service.reject(id, actor, longReason))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "VALIDATION_ERROR|Lý do từ chối phải từ 3 đến 500 ký tự.");
                });
    }

    @Test
    void rejectThrowsNotFoundWhenRequestDoesNotExist() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();
        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(id, actor, "Ly do hop le"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_NOT_FOUND|Không tìm thấy yêu cầu hoàn cọc.");
                });
    }

    @Test
    void rejectThrowsConflictWhenAlreadyProcessed() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund row = pendingRow(id, orderId, customerId);
        row.setStatus(OrderCancellationRefund.STATUS_REFUNDED);
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();

        when(refundRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.reject(id, actor, "Ly do hop le"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_ALREADY_PROCESSED|Yêu cầu hoàn cọc đã được xử lý.");
                });
    }

    // ===================== misc =====================

    @Test
    void defaultPageSizeIsTwenty() {
        assertThat(service.defaultPageSize()).isEqualTo(20);
    }
}
