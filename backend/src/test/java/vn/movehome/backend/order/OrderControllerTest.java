package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.order.CreateOrderRequest.Location;
import vn.movehome.backend.payment.WalletOrderPaymentService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho OrderController — plain new-instance pattern (khong load Spring context),
 * chi kiem tra controller delegate dung tham so (customerId tu JWT principal, HR-10).
 */
class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final CustomerOrderActionService customerOrderActionService = mock(CustomerOrderActionService.class);
    private final CustomerOrderQueryService customerOrderQueryService = mock(CustomerOrderQueryService.class);
    private final WalletOrderPaymentService walletOrderPaymentService = mock(WalletOrderPaymentService.class);
    private final OrderCancellationPhotoService orderCancellationPhotoService = mock(OrderCancellationPhotoService.class);

    private OrderController controller;
    private User customer;

    @BeforeEach
    void setUp() {
        controller = new OrderController(
                orderService,
                customerOrderActionService,
                customerOrderQueryService,
                walletOrderPaymentService,
                orderCancellationPhotoService);
        customer = User.builder().id(UUID.randomUUID()).role(UserRole.CUSTOMER).build();
    }

    @Test
    void getMyOrders_delegatesWithCustomerIdScopeAndPageable() {
        Page<CustomerOrderItem> expected = new PageImpl<>(List.of());
        when(customerOrderQueryService.getMyOrders(customer.getId(), "history", PageRequest.of(1, 10)))
                .thenReturn(expected);

        Page<CustomerOrderItem> actual = controller.getMyOrders(customer, "history", 1, 10);

        assertThat(actual).isSameAs(expected);
        verify(customerOrderQueryService).getMyOrders(customer.getId(), "history", PageRequest.of(1, 10));
    }

    @Test
    void getOrderDetail_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();
        CustomerOrderDetailResponse expected = new CustomerOrderDetailResponse(
                orderId, "MH1", "PENDING", "TRUCK_500KG", "addr", "BA_DINH",
                (BigDecimal) null, (BigDecimal) null, "addr2", "TAY_HO", (BigDecimal) null, (BigDecimal) null,
                OffsetDateTime.now(ZoneOffset.UTC), (BigDecimal) null, (BigDecimal) null, (BigDecimal) null,
                (BigDecimal) null, (BigDecimal) null, (BigDecimal) null, 0,
                new BigDecimal("1000000"), new BigDecimal("300000"), true,
                new BigDecimal("700000"), false, (String) null, (String) null,
                OffsetDateTime.now(ZoneOffset.UTC), (OffsetDateTime) null, (OffsetDateTime) null);
        when(customerOrderQueryService.getOrderDetail(customer.getId(), orderId)).thenReturn(expected);

        CustomerOrderDetailResponse actual = controller.getOrderDetail(customer, orderId);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderQueryService).getOrderDetail(customer.getId(), orderId);
    }

    @Test
    void getOrderRoute_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();
        List<double[]> expected = List.of(new double[]{21.0, 105.0});
        when(customerOrderQueryService.getOrderRoute(customer.getId(), orderId)).thenReturn(expected);

        List<double[]> actual = controller.getOrderRoute(customer, orderId);

        assertThat(actual).isSameAs(expected);
        verify(customerOrderQueryService).getOrderRoute(customer.getId(), orderId);
    }

    @Test
    void createOrder_delegatesWithCustomerId() {
        CreateOrderRequest request = new CreateOrderRequest(
                "TRUCK_500KG",
                new Location("So 1 pho ABC dai du", "BA_DINH", new BigDecimal("21.0"), new BigDecimal("105.0"), 1, true, false),
                new Location("So 2 pho XYZ dai du", "TAY_HO", new BigDecimal("21.1"), new BigDecimal("105.1"), 1, true, false),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                0,
                null);
        CreateOrderResponse expected = new CreateOrderResponse(
                UUID.randomUUID(), "MH1", "PENDING_PAYMENT", new BigDecimal("500000"));
        when(orderService.createOrder(customer.getId(), request)).thenReturn(expected);

        CreateOrderResponse actual = controller.createOrder(customer, request);

        assertThat(actual).isEqualTo(expected);
        verify(orderService).createOrder(customer.getId(), request);
    }

    @Test
    void cancelOrder_delegatesWithCustomerIdAndRole() {
        UUID orderId = UUID.randomUUID();
        CancelOrderRequest request = new CancelOrderRequest("Doi y");
        CancelOrderResponse expected = new CancelOrderResponse(
                orderId, "CANCELLED", OffsetDateTime.now(ZoneOffset.UTC), "Đơn hàng đã được hủy.");
        when(customerOrderActionService.cancelOrder(customer.getId(), "CUSTOMER", orderId, request))
                .thenReturn(expected);

        CancelOrderResponse actual = controller.cancelOrder(customer, orderId, request);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderActionService).cancelOrder(customer.getId(), "CUSTOMER", orderId, request);
    }

    @Test
    void uploadCancellationPhoto_delegatesWithOrderIdAndCustomerId() {
        UUID orderId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        controller.uploadCancellationPhoto(customer, orderId, file);

        verify(orderCancellationPhotoService).uploadByOrder(orderId, customer.getId(), file);
    }

    @Test
    void getDriverVerification_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();
        CustomerDriverVerificationResponse expected = new CustomerDriverVerificationResponse(
                "Tai Xe", "+8490", "TRUCK_500KG", "30A-1", "face.jpg", "vehicle.jpg", true);
        when(customerOrderQueryService.getDriverVerification(customer.getId(), orderId)).thenReturn(expected);

        CustomerDriverVerificationResponse actual = controller.getDriverVerification(customer, orderId);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderQueryService).getDriverVerification(customer.getId(), orderId);
    }

    @Test
    void reportDriverMismatch_delegatesWithCustomerIdAndRole() {
        UUID orderId = UUID.randomUUID();
        CancelOrderResponse expected = new CancelOrderResponse(
                orderId, "CANCELLED", OffsetDateTime.now(ZoneOffset.UTC), "Đã hủy chuyến và gửi khiếu nại cho quản lý xem xét hoàn cọc.");
        when(customerOrderActionService.reportDriverMismatch(customer.getId(), "CUSTOMER", orderId))
                .thenReturn(expected);

        CancelOrderResponse actual = controller.reportDriverMismatch(customer, orderId);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderActionService).reportDriverMismatch(customer.getId(), "CUSTOMER", orderId);
    }

    @Test
    void confirmDriverMatch_delegatesWithCustomerIdAndRole() {
        UUID orderId = UUID.randomUUID();
        CancelOrderResponse expected = new CancelOrderResponse(
                orderId, "IN_PROGRESS", null, "Đã xác nhận đúng tài xế/xe. Chuyến bắt đầu.");
        when(customerOrderActionService.confirmDriverMatch(customer.getId(), "CUSTOMER", orderId))
                .thenReturn(expected);

        CancelOrderResponse actual = controller.confirmDriverMatch(customer, orderId);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderActionService).confirmDriverMatch(customer.getId(), "CUSTOMER", orderId);
    }

    @Test
    void rateOrder_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();
        RatingRequest request = new RatingRequest(5, "Tot");
        RatingResponse expected = new RatingResponse(
                UUID.randomUUID(), orderId, 5, "Cảm ơn bạn đã đánh giá đơn hàng.");
        when(customerOrderActionService.rateOrder(customer.getId(), orderId, request)).thenReturn(expected);

        RatingResponse actual = controller.rateOrder(customer, orderId, request);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderActionService).rateOrder(customer.getId(), orderId, request);
    }

    @Test
    void getOrderRating_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();
        RatingDetailResponse expected = new RatingDetailResponse(
                UUID.randomUUID(), orderId, 5, "Tot", OffsetDateTime.now(ZoneOffset.UTC));
        when(customerOrderActionService.getOrderRating(customer.getId(), orderId)).thenReturn(expected);

        RatingDetailResponse actual = controller.getOrderRating(customer, orderId);

        assertThat(actual).isEqualTo(expected);
        verify(customerOrderActionService).getOrderRating(customer.getId(), orderId);
    }

    @Test
    void payDepositFromWallet_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();

        controller.payDepositFromWallet(customer, orderId);

        verify(walletOrderPaymentService).payDepositFromWallet(customer.getId(), orderId);
    }

    @Test
    void payFinalFromWallet_delegatesWithCustomerId() {
        UUID orderId = UUID.randomUUID();

        controller.payFinalFromWallet(customer, orderId);

        verify(walletOrderPaymentService).payFinalFromWallet(customer.getId(), orderId);
    }
}
