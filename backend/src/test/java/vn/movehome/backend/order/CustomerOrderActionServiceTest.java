package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.DisputeService;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.repository.DriverProfileRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho CustomerOrderActionService: huy don, bao cao khong khop tai xe/xe,
 * xac nhan dung tai xe, danh gia don.
 */
@ExtendWith(MockitoExtension.class)
class CustomerOrderActionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusTransitionService orderStatusTransitionService;

    @Mock
    private OrderRatingRepository orderRatingRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DisputeService disputeService;

    @Mock
    private OrderCancellationRefundService cancellationRefundService;

    private CustomerOrderActionService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private static final String ROLE = "CUSTOMER";

    @BeforeEach
    void setUp() {
        service = new CustomerOrderActionService(
                orderRepository,
                orderStatusTransitionService,
                orderRatingRepository,
                driverProfileRepository,
                disputeService,
                cancellationRefundService);
    }

    private ServiceOrder orderWithStatus(String status) {
        return ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH202607220001")
                .customerId(customerId)
                .status(status)
                .totalQuote(new BigDecimal("1000000"))
                .build();
    }

    // ===================== cancelOrder =====================

    @Test
    void cancelOrder_nullRequest_throwsValidationError() {
        assertThatThrownBy(() -> service.cancelOrder(customerId, ROLE, orderId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập lý do hủy đơn.");
                });
        verify(orderRepository, never()).findByIdAndCustomerIdForUpdate(any(), any());
    }

    @Test
    void cancelOrder_blankReason_throwsValidationError() {
        assertThatThrownBy(() -> service.cancelOrder(customerId, ROLE, orderId, new CancelOrderRequest("   ")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng nhập lý do hủy đơn.");
                });
    }

    @Test
    void cancelOrder_reasonTooLong_throwsValidationError() {
        String longReason = "a".repeat(501);
        assertThatThrownBy(() -> service.cancelOrder(customerId, ROLE, orderId, new CancelOrderRequest(longReason)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Lý do hủy đơn không được vượt quá 500 ký tự.");
                });
    }

    @Test
    void cancelOrder_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelOrder(customerId, ROLE, orderId, new CancelOrderRequest("Doi y")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
                });
    }

    @Test
    void cancelOrder_statusNotCancellable_throwsIllegalState() {
        ServiceOrder order = orderWithStatus("IN_PROGRESS");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(customerId, ROLE, orderId, new CancelOrderRequest("Doi y")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chỉ có thể hủy đơn ở trạng thái đang chờ xử lý.");
    }

    @Test
    void cancelOrder_confirmedStatus_opensRefundRequestAndReturnsMessage() {
        ServiceOrder order = orderWithStatus("CONFIRMED");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        ServiceOrder savedOrder = orderWithStatus("CANCELLED");
        savedOrder.setCancelledAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(orderStatusTransitionService.transition(eq(order), eq("CANCELLED"), eq(customerId), eq(ROLE), any()))
                .thenReturn(savedOrder);

        CancelOrderResponse response = service.cancelOrder(
                customerId, ROLE, orderId, new CancelOrderRequest("Doi y dinh"));

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.message())
                .isEqualTo("Đơn hàng đã được hủy. Yêu cầu hoàn cọc đã gửi tới quản lý để xem xét.");
        verify(cancellationRefundService).openForCancelledOrder(savedOrder, customerId, "Doi y dinh");
        assertThat(order.getCancellationReason()).isEqualTo("Doi y dinh");
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelOrder_pendingStatus_noRefundRequestAndDefaultMessage() {
        ServiceOrder order = orderWithStatus("PENDING");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        ServiceOrder savedOrder = orderWithStatus("CANCELLED");
        when(orderStatusTransitionService.transition(eq(order), eq("CANCELLED"), eq(customerId), eq(ROLE), any()))
                .thenReturn(savedOrder);

        CancelOrderResponse response = service.cancelOrder(
                customerId, ROLE, orderId, new CancelOrderRequest("Khong can nua"));

        assertThat(response.message()).isEqualTo("Đơn hàng đã được hủy.");
        verify(cancellationRefundService, never()).openForCancelledOrder(any(), any(), any());
    }

    @Test
    void cancelOrder_pendingPaymentStatus_noRefundRequest() {
        ServiceOrder order = orderWithStatus("PENDING_PAYMENT");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        ServiceOrder savedOrder = orderWithStatus("CANCELLED");
        when(orderStatusTransitionService.transition(eq(order), eq("CANCELLED"), eq(customerId), eq(ROLE), any()))
                .thenReturn(savedOrder);

        CancelOrderResponse response = service.cancelOrder(
                customerId, ROLE, orderId, new CancelOrderRequest("Khong can nua"));

        assertThat(response.message()).isEqualTo("Đơn hàng đã được hủy.");
        verify(cancellationRefundService, never()).openForCancelledOrder(any(), any(), any());
    }

    // ===================== reportDriverMismatch =====================

    @Test
    void reportDriverMismatch_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportDriverMismatch(customerId, ROLE, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void reportDriverMismatch_statusNotAccepted_throwsIllegalState() {
        ServiceOrder order = orderWithStatus("PENDING");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportDriverMismatch(customerId, ROLE, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chỉ có thể báo cáo không khớp khi tài xế vừa nhận đơn và chưa bắt đầu vận chuyển.");
    }

    @Test
    void reportDriverMismatch_notArrived_throwsConflict() {
        ServiceOrder order = orderWithStatus("ACCEPTED");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportDriverMismatch(customerId, ROLE, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("NOT_ARRIVED|Chỉ báo cáo không khớp sau khi tài xế đã đến điểm đón.");
                });
    }

    @Test
    void reportDriverMismatch_success_cancelsAndOpensDispute() {
        ServiceOrder order = orderWithStatus("ACCEPTED");
        order.setArrivedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        ServiceOrder savedOrder = orderWithStatus("CANCELLED");
        when(orderStatusTransitionService.transition(eq(order), eq("CANCELLED"), eq(customerId), eq(ROLE), any()))
                .thenReturn(savedOrder);

        CancelOrderResponse response = service.reportDriverMismatch(customerId, ROLE, orderId);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.message()).isEqualTo("Đã hủy chuyến và gửi khiếu nại cho quản lý xem xét hoàn cọc.");
        assertThat(order.getCancellationReason())
                .isEqualTo("Tài xế hoặc phương tiện không khớp ảnh xác thực (khách báo cáo).");
        verify(disputeService).openMismatchDispute(savedOrder, customerId);
    }

    // ===================== confirmDriverMatch =====================

    @Test
    void confirmDriverMatch_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmDriverMatch(customerId, ROLE, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmDriverMatch_statusNotAccepted_throwsConflict() {
        ServiceOrder order = orderWithStatus("PENDING");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmDriverMatch(customerId, ROLE, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("INVALID_STATE|Chỉ xác nhận khi tài xế đang ở trạng thái đã nhận đơn.");
                });
    }

    @Test
    void confirmDriverMatch_notArrived_throwsConflict() {
        ServiceOrder order = orderWithStatus("ACCEPTED");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmDriverMatch(customerId, ROLE, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("NOT_ARRIVED|Tài xế chưa đến điểm đón để đối chiếu.");
                });
    }

    @Test
    void confirmDriverMatch_success_startsTrip() {
        ServiceOrder order = orderWithStatus("ACCEPTED");
        order.setArrivedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        ServiceOrder savedOrder = orderWithStatus("IN_PROGRESS");
        when(orderStatusTransitionService.transition(eq(order), eq("IN_PROGRESS"), eq(customerId), eq(ROLE), any()))
                .thenReturn(savedOrder);

        CancelOrderResponse response = service.confirmDriverMatch(customerId, ROLE, orderId);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.cancelledAt()).isNull();
        assertThat(response.message()).isEqualTo("Đã xác nhận đúng tài xế/xe. Chuyến bắt đầu.");
        assertThat(order.getStartedAt()).isNotNull();
    }

    // ===================== rateOrder =====================

    @Test
    void rateOrder_starsNull_throwsValidationError() {
        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(null, "Tot")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số sao đánh giá phải từ 1 đến 5.");
                });
        verify(orderRepository, never()).findByIdAndCustomerIdForUpdate(any(), any());
    }

    @Test
    void rateOrder_starsOutOfRange_throwsValidationError() {
        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(0, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số sao đánh giá phải từ 1 đến 5."));
        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(6, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số sao đánh giá phải từ 1 đến 5."));
    }

    @Test
    void rateOrder_commentTooLong_throwsValidationError() {
        String longComment = "a".repeat(501);
        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(5, longComment)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("VALIDATION_ERROR|Nhận xét không được vượt quá 500 ký tự.");
                });
    }

    @Test
    void rateOrder_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(5, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rateOrder_statusNotCompleted_throwsIllegalState() {
        ServiceOrder order = orderWithStatus("IN_PROGRESS");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(5, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chỉ có thể đánh giá đơn hàng đã hoàn thành.");
    }

    @Test
    void rateOrder_windowClosed_wholeHours_throwsConflict() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 120L);
        ServiceOrder order = orderWithStatus("COMPLETED");
        order.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(5, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "RATING_WINDOW_CLOSED|Đã quá thời hạn đánh giá. Bạn chỉ có thể đánh giá trong "
                                    + "2 giờ sau khi đơn hoàn thành.");
                });
    }

    @Test
    void rateOrder_windowClosed_partialMinutes_throwsConflict() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 90L);
        ServiceOrder order = orderWithStatus("COMPLETED");
        order.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(5, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(
                                "RATING_WINDOW_CLOSED|Đã quá thời hạn đánh giá. Bạn chỉ có thể đánh giá trong "
                                        + "90 phút sau khi đơn hoàn thành."));
    }

    @Test
    void rateOrder_alreadyRated_throwsConflict() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 0L);
        ServiceOrder order = orderWithStatus("COMPLETED");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.existsByOrderId(orderId)).thenReturn(true);

        assertThatThrownBy(() -> service.rateOrder(customerId, orderId, new RatingRequest(5, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo("ORDER_ALREADY_RATED|Đơn hàng này đã được đánh giá.");
                });
    }

    @Test
    void rateOrder_success_withoutDriver_skipsAverageUpdate() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 0L);
        ServiceOrder order = orderWithStatus("COMPLETED"); // driverId null mac dinh
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.existsByOrderId(orderId)).thenReturn(false);

        UUID ratingId = UUID.randomUUID();
        when(orderRatingRepository.saveAndFlush(any(OrderRating.class))).thenAnswer(invocation -> {
            OrderRating rating = invocation.getArgument(0);
            rating.setId(ratingId);
            return rating;
        });

        RatingResponse response = service.rateOrder(customerId, orderId, new RatingRequest(4, "  Tot  "));

        assertThat(response.ratingId()).isEqualTo(ratingId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.stars()).isEqualTo(4);
        assertThat(response.message()).isEqualTo("Cảm ơn bạn đã đánh giá đơn hàng.");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void rateOrder_success_driverProfileAbsent_skipsAverageUpdate() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 0L);
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("COMPLETED");
        order.setDriverId(driverId);
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.existsByOrderId(orderId)).thenReturn(false);
        when(orderRatingRepository.saveAndFlush(any(OrderRating.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());

        service.rateOrder(customerId, orderId, new RatingRequest(3, null));

        verify(orderRatingRepository, never()).calculateAverageStarsByDriverId(any());
        verify(driverProfileRepository, never()).save(any());
    }

    @Test
    void rateOrder_success_averageZero_defaultsTo5Stars() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 0L);
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("COMPLETED");
        order.setDriverId(driverId);
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.existsByOrderId(orderId)).thenReturn(false);
        when(orderRatingRepository.saveAndFlush(any(OrderRating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DriverProfile profile = DriverProfile.builder().userId(driverId).build();
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(orderRatingRepository.calculateAverageStarsByDriverId(driverId)).thenReturn(BigDecimal.ZERO);

        service.rateOrder(customerId, orderId, new RatingRequest(5, null));

        assertThat(profile.getAverageRating()).isEqualByComparingTo(new BigDecimal("5.00"));
        verify(driverProfileRepository).save(profile);
    }

    @Test
    void rateOrder_success_averageNonZero_setsScale2() {
        ReflectionTestUtils.setField(service, "ratingWindowMinutes", 0L);
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("COMPLETED");
        order.setDriverId(driverId);
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.existsByOrderId(orderId)).thenReturn(false);
        when(orderRatingRepository.saveAndFlush(any(OrderRating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DriverProfile profile = DriverProfile.builder().userId(driverId).build();
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(orderRatingRepository.calculateAverageStarsByDriverId(driverId)).thenReturn(new BigDecimal("3.456"));

        service.rateOrder(customerId, orderId, new RatingRequest(3, null));

        assertThat(profile.getAverageRating()).isEqualByComparingTo(new BigDecimal("3.46"));
        verify(driverProfileRepository).save(profile);
    }

    // ===================== getOrderRating =====================

    @Test
    void getOrderRating_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderRating(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
                });
    }

    @Test
    void getOrderRating_ratingNotFound_throws404() {
        ServiceOrder order = orderWithStatus("COMPLETED");
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderRating(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("RATING_NOT_FOUND|Đơn hàng này chưa được đánh giá.");
                });
    }

    @Test
    void getOrderRating_success_returnsDetail() {
        ServiceOrder order = orderWithStatus("COMPLETED");
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)).thenReturn(Optional.of(order));

        UUID ratingId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        OrderRating rating = OrderRating.builder()
                .id(ratingId)
                .orderId(orderId)
                .customerId(customerId)
                .stars(4)
                .comment("Rat tot")
                .createdAt(createdAt)
                .build();
        when(orderRatingRepository.findByOrderId(orderId)).thenReturn(Optional.of(rating));

        RatingDetailResponse response = service.getOrderRating(customerId, orderId);

        assertThat(response.ratingId()).isEqualTo(ratingId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.stars()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Rat tot");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
