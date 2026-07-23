package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.DriverDocumentService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho CustomerOrderQueryService: danh sach + chi tiet don, anh xac thuc tai xe, tuyen duong.
 * Toan bo query filter theo customerId (HR-10).
 */
@ExtendWith(MockitoExtension.class)
class CustomerOrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OsrmClient osrmClient;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverDocumentService driverDocumentService;

    private CustomerOrderQueryService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CustomerOrderQueryService(
                orderRepository, userRepository, osrmClient, driverProfileRepository, driverDocumentService);
    }

    private ServiceOrder orderWithStatus(String status) {
        return ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH202607220001")
                .customerId(customerId)
                .status(status)
                .totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
    }

    // ===================== getMyOrders =====================

    @Test
    void getMyOrders_historyScope_queriesTerminalStatuses() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CustomerOrderItem> expected = new PageImpl<>(List.of());
        when(orderRepository.findCustomerOrdersByStatusIn(eq(customerId), anyCollection(), eq(pageable)))
                .thenReturn(expected);

        Page<CustomerOrderItem> result = service.getMyOrders(customerId, "history", pageable);

        assertThat(result).isSameAs(expected);
        verify(orderRepository).findCustomerOrdersByStatusIn(customerId, List.of("COMPLETED", "CANCELLED"), pageable);
        verify(orderRepository, never()).findCustomerOrdersByStatusNotIn(any(), any(), any());
    }

    @Test
    void getMyOrders_historyScope_caseInsensitive() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CustomerOrderItem> expected = new PageImpl<>(List.of());
        when(orderRepository.findCustomerOrdersByStatusIn(eq(customerId), anyCollection(), eq(pageable)))
                .thenReturn(expected);

        service.getMyOrders(customerId, "HISTORY", pageable);

        verify(orderRepository).findCustomerOrdersByStatusIn(eq(customerId), anyCollection(), eq(pageable));
    }

    @Test
    void getMyOrders_defaultScope_queriesNonTerminalStatuses() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CustomerOrderItem> expected = new PageImpl<>(List.of());
        when(orderRepository.findCustomerOrdersByStatusNotIn(eq(customerId), anyCollection(), eq(pageable)))
                .thenReturn(expected);

        Page<CustomerOrderItem> result = service.getMyOrders(customerId, "pending", pageable);

        assertThat(result).isSameAs(expected);
        verify(orderRepository).findCustomerOrdersByStatusNotIn(
                customerId, List.of("COMPLETED", "CANCELLED"), pageable);
    }

    @Test
    void getMyOrders_unknownScope_defaultsToNonTerminal() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findCustomerOrdersByStatusNotIn(eq(customerId), anyCollection(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getMyOrders(customerId, "unknown-scope", pageable);

        verify(orderRepository).findCustomerOrdersByStatusNotIn(eq(customerId), anyCollection(), eq(pageable));
        verify(orderRepository, never()).findCustomerOrdersByStatusIn(any(), any(), any());
    }

    // ===================== getOrderDetail =====================

    @Test
    void getOrderDetail_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderDetail(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
                });
    }

    @Test
    void getOrderDetail_noDriverAssigned_driverNameNull() {
        ServiceOrder order = orderWithStatus("PENDING_PAYMENT");
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        CustomerOrderDetailResponse response = service.getOrderDetail(customerId, orderId);

        assertThat(response.driverName()).isNull();
        assertThat(response.depositPaid()).isFalse();
        assertThat(response.finalPaid()).isFalse();
        verify(userRepository, never()).findById(any());
        // deposit = 1000000 * 0.3000 floor = 300000; remaining = 700000
        assertThat(response.depositAmount()).isEqualByComparingTo(new BigDecimal("300000"));
        assertThat(response.remainingAmount()).isEqualByComparingTo(new BigDecimal("700000"));
    }

    @Test
    void getOrderDetail_driverAssignedAndFound_driverNameSet() {
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("CONFIRMED");
        order.setDriverId(driverId);
        order.setFinalPaidAt(java.time.OffsetDateTime.now());
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        User driver = User.builder().id(driverId).fullName("Nguyen Van Tai").build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

        CustomerOrderDetailResponse response = service.getOrderDetail(customerId, orderId);

        assertThat(response.driverName()).isEqualTo("Nguyen Van Tai");
        assertThat(response.depositPaid()).isTrue();
        assertThat(response.finalPaid()).isTrue();
    }

    @Test
    void getOrderDetail_driverAssignedButUserMissing_driverNameNull() {
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("CONFIRMED");
        order.setDriverId(driverId);
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        CustomerOrderDetailResponse response = service.getOrderDetail(customerId, orderId);

        assertThat(response.driverName()).isNull();
    }

    @Test
    void getOrderDetail_pendingStatus_depositNotPaid() {
        ServiceOrder order = orderWithStatus("PENDING");
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        CustomerOrderDetailResponse response = service.getOrderDetail(customerId, orderId);

        assertThat(response.depositPaid()).isFalse();
    }

    // ===================== getDriverVerification =====================

    @Test
    void getDriverVerification_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDriverVerification(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDriverVerification_noDriverAssigned_throwsConflict() {
        ServiceOrder order = orderWithStatus("PENDING");
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getDriverVerification(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason())
                            .isEqualTo("NO_DRIVER_ASSIGNED|Đơn chưa có tài xế nhận nên chưa có ảnh xác thực.");
                });
    }

    @Test
    void getDriverVerification_success_withFrontPhotoAndAccepted() {
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("ACCEPTED");
        order.setDriverId(driverId);
        order.setArrivedAt(java.time.OffsetDateTime.now());
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        User driver = User.builder().id(driverId).fullName("Tran Van Xe").phone("+84900000000").build();
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .vehicleType("TRUCK_1T")
                .vehiclePlate("30A-12345")
                .build();
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        when(driverDocumentService.latestSignedUrlsByType(
                eq(driverId), eq(Set.of("FACE_PHOTO", "VEHICLE_PHOTO_FRONT", "VEHICLE_PHOTO"))))
                .thenReturn(Map.of(
                        "FACE_PHOTO", "https://face.jpg",
                        "VEHICLE_PHOTO_FRONT", "https://front.jpg",
                        "VEHICLE_PHOTO", "https://old.jpg"));

        CustomerDriverVerificationResponse response = service.getDriverVerification(customerId, orderId);

        assertThat(response.driverName()).isEqualTo("Tran Van Xe");
        assertThat(response.driverPhone()).isEqualTo("+84900000000");
        assertThat(response.vehicleType()).isEqualTo("TRUCK_1T");
        assertThat(response.vehiclePlate()).isEqualTo("30A-12345");
        assertThat(response.facePhotoUrl()).isEqualTo("https://face.jpg");
        assertThat(response.vehiclePhotoUrl()).isEqualTo("https://front.jpg");
        assertThat(response.cancellable()).isTrue();
    }

    @Test
    void getDriverVerification_fallsBackToLegacyVehiclePhotoWhenNoFrontPhoto() {
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("ACCEPTED");
        order.setDriverId(driverId);
        order.setArrivedAt(java.time.OffsetDateTime.now());
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverDocumentService.latestSignedUrlsByType(eq(driverId), any()))
                .thenReturn(Map.of("VEHICLE_PHOTO", "https://legacy.jpg"));

        CustomerDriverVerificationResponse response = service.getDriverVerification(customerId, orderId);

        assertThat(response.driverName()).isNull();
        assertThat(response.driverPhone()).isNull();
        // Profile absent -> fallback to order.vehicleType, plate null
        assertThat(response.vehicleType()).isEqualTo(order.getVehicleType());
        assertThat(response.vehiclePlate()).isNull();
        assertThat(response.vehiclePhotoUrl()).isEqualTo("https://legacy.jpg");
    }

    @Test
    void getDriverVerification_notAcceptedStatus_notCancellable() {
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("IN_PROGRESS");
        order.setDriverId(driverId);
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverDocumentService.latestSignedUrlsByType(eq(driverId), any())).thenReturn(Map.of());

        CustomerDriverVerificationResponse response = service.getDriverVerification(customerId, orderId);

        assertThat(response.cancellable()).isFalse();
    }

    @Test
    void getDriverVerification_acceptedButNotArrived_notCancellable() {
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = orderWithStatus("ACCEPTED");
        order.setDriverId(driverId);
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverDocumentService.latestSignedUrlsByType(eq(driverId), any())).thenReturn(Map.of());

        CustomerDriverVerificationResponse response = service.getDriverVerification(customerId, orderId);

        assertThat(response.cancellable()).isFalse();
    }

    // ===================== getOrderRoute =====================

    @Test
    void getOrderRoute_orderNotFound_throws404() {
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderRoute(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getOrderRoute_missingCoordinates_returnsEmptyList() {
        ServiceOrder order = orderWithStatus("CONFIRMED"); // lat/lng null mac dinh
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        List<double[]> result = service.getOrderRoute(customerId, orderId);

        assertThat(result).isEmpty();
        verify(osrmClient, never()).fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void getOrderRoute_success_delegatesToOsrmClient() {
        ServiceOrder order = orderWithStatus("CONFIRMED");
        order.setPickupLat(new BigDecimal("21.0000000"));
        order.setPickupLng(new BigDecimal("105.0000000"));
        order.setDropoffLat(new BigDecimal("21.1000000"));
        order.setDropoffLng(new BigDecimal("105.1000000"));
        when(orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId))
                .thenReturn(Optional.of(order));

        List<double[]> expected = List.of(new double[]{21.0, 105.0}, new double[]{21.1, 105.1});
        when(osrmClient.fetchRouteGeometry(21.0, 105.0, 21.1, 105.1)).thenReturn(expected);

        List<double[]> result = service.getOrderRoute(customerId, orderId);

        assertThat(result).isSameAs(expected);
        verify(osrmClient, times(1)).fetchRouteGeometry(21.0, 105.0, 21.1, 105.1);
    }
}
