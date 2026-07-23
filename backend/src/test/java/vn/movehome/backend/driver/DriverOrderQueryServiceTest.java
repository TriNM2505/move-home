package vn.movehome.backend.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import static org.mockito.Mockito.when;

class DriverOrderQueryServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private DriverOrderQueryService service;

    private final UUID driverId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DriverOrderQueryService(orderRepository, userRepository);
    }

    private ServiceOrder.ServiceOrderBuilder baseOrder() {
        return ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-0001")
                .customerId(customerId)
                .vehicleType("TRUCK_500KG")
                .pickupDistrict("Cầu Giấy")
                .dropoffDistrict("Đống Đa")
                .scheduledAt(OffsetDateTime.now(ZoneOffset.UTC))
                .distanceKm(new BigDecimal("5.50"))
                .totalQuote(new BigDecimal("500000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"));
    }

    // ================= getAvailableOrders =================

    @Test
    void getAvailableOrders_mapsToListItems() {
        ServiceOrder order = baseOrder().status("CONFIRMED").build();
        Page<ServiceOrder> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusAndDeletedAtIsNull(eq("CONFIRMED"), any(Pageable.class)))
                .thenReturn(page);

        Page<DriverOrderListItemDTO> result = service.getAvailableOrders(0, 10);

        assertThat(result.getContent()).hasSize(1);
        DriverOrderListItemDTO dto = result.getContent().get(0);
        assertThat(dto.id()).isEqualTo(orderId);
        assertThat(dto.orderCode()).isEqualTo("MH-0001");
        assertThat(dto.status()).isEqualTo("CONFIRMED");
        assertThat(dto.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(dto.pickupDistrict()).isEqualTo("Cầu Giấy");
        assertThat(dto.dropoffDistrict()).isEqualTo("Đống Đa");
        assertThat(dto.distanceKm()).isEqualByComparingTo("5.50");
        assertThat(dto.totalQuote()).isEqualByComparingTo("500000");
        assertThat(dto.commissionRateSnapshot()).isEqualByComparingTo("0.3000");
    }

    // ================= createPageRequest (via getAvailableOrders) =================

    @Test
    void getAvailableOrders_throwsWhenPageNegative() {
        assertThatThrownBy(() -> service.getAvailableOrders(-1, 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR|Số trang không hợp lệ.");
    }

    @Test
    void getAvailableOrders_throwsWhenSizeZero() {
        assertThatThrownBy(() -> service.getAvailableOrders(0, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
    }

    @Test
    void getAvailableOrders_throwsWhenSizeTooLarge() {
        assertThatThrownBy(() -> service.getAvailableOrders(0, 101))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
    }

    // ================= getDriverOrders / normalizeStatus =================

    @Test
    void getDriverOrders_withNullStatus_queriesWithoutStatusFilter() {
        Page<ServiceOrder> page = new PageImpl<>(List.of());
        when(orderRepository.findByDriverIdAndDeletedAtIsNull(eq(driverId), any(Pageable.class)))
                .thenReturn(page);

        Page<DriverOrderListItemDTO> result = service.getDriverOrders(driverId, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository, never()).findByDriverIdAndStatusAndDeletedAtIsNull(any(), anyString(), any());
    }

    @Test
    void getDriverOrders_withBlankStatus_treatedAsNull() {
        when(orderRepository.findByDriverIdAndDeletedAtIsNull(eq(driverId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getDriverOrders(driverId, "   ", 0, 10);

        verify(orderRepository).findByDriverIdAndDeletedAtIsNull(eq(driverId), any(Pageable.class));
    }

    @Test
    void getDriverOrders_withAllStatus_caseInsensitive_treatedAsNull() {
        when(orderRepository.findByDriverIdAndDeletedAtIsNull(eq(driverId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getDriverOrders(driverId, "all", 0, 10);

        verify(orderRepository).findByDriverIdAndDeletedAtIsNull(eq(driverId), any(Pageable.class));
    }

    @Test
    void getDriverOrders_withValidStatus_normalizesToUppercaseAndFilters() {
        Page<ServiceOrder> page = new PageImpl<>(List.of());
        when(orderRepository.findByDriverIdAndStatusAndDeletedAtIsNull(eq(driverId), eq("ACCEPTED"), any(Pageable.class)))
                .thenReturn(page);

        service.getDriverOrders(driverId, "accepted", 0, 10);

        verify(orderRepository).findByDriverIdAndStatusAndDeletedAtIsNull(eq(driverId), eq("ACCEPTED"), any(Pageable.class));
    }

    @Test
    void getDriverOrders_withInvalidStatus_throwsValidationError() {
        assertThatThrownBy(() -> service.getDriverOrders(driverId, "NOT_A_STATUS", 0, 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR|Trạng thái đơn hàng không hợp lệ.");
    }

    // ================= getOrderDetail =================

    @Test
    void getOrderDetail_throwsNotFoundWhenMissing() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderDetail(driverId, orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
    }

    @Test
    void getOrderDetail_throwsNotFoundWhenSoftDeleted() {
        ServiceOrder order = baseOrder().status("CONFIRMED").deletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrderDetail(driverId, orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    @Test
    void getOrderDetail_throwsNotFoundWhenNotAvailableAndNotAssignedToDriver() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(UUID.randomUUID()).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrderDetail(driverId, orderId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    @Test
    void getOrderDetail_returnsDetail_whenAvailableForPickup() {
        ServiceOrder order = baseOrder().status("CONFIRMED").driverId(null).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DriverOrderDetailDTO dto = service.getOrderDetail(driverId, orderId);

        assertThat(dto.available()).isTrue();
        assertThat(dto.assignedToCurrentDriver()).isFalse();
        assertThat(dto.customerName()).isNull();
        assertThat(dto.customerPhone()).isNull();
        verify(userRepository).findById(order.getCustomerId());
    }

    @Test
    void getOrderDetail_returnsDetail_whenAssignedToDriver_withCustomerInfo() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId)
                .finalPaidAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        User customer = User.builder().id(customerId).fullName("Nguyễn Văn A").phone("+84912345678").build();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        DriverOrderDetailDTO dto = service.getOrderDetail(driverId, orderId);

        assertThat(dto.available()).isFalse();
        assertThat(dto.assignedToCurrentDriver()).isTrue();
        assertThat(dto.finalPaid()).isTrue();
        assertThat(dto.customerName()).isEqualTo("Nguyễn Văn A");
        assertThat(dto.customerPhone()).isEqualTo("+84912345678");
        assertThat(dto.id()).isEqualTo(orderId);
        assertThat(dto.driverId()).isEqualTo(driverId);
    }

    @Test
    void getOrderDetail_whenCustomerIdNull_customerFieldsAreNull() {
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId).orderCode("MH-0002").customerId(null)
                .status("ACCEPTED").driverId(driverId)
                .scheduledAt(OffsetDateTime.now(ZoneOffset.UTC))
                .totalQuote(new BigDecimal("500000"))
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DriverOrderDetailDTO dto = service.getOrderDetail(driverId, orderId);

        assertThat(dto.customerName()).isNull();
        assertThat(dto.customerPhone()).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getOrderDetail_whenCustomerLookupReturnsEmpty_customerFieldsAreNull() {
        ServiceOrder order = baseOrder().status("ACCEPTED").driverId(driverId).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        DriverOrderDetailDTO dto = service.getOrderDetail(driverId, orderId);

        assertThat(dto.customerName()).isNull();
        assertThat(dto.customerPhone()).isNull();
    }

    // ================= defaultPageSize =================

    @Test
    void defaultPageSize_returns10() {
        assertThat(service.defaultPageSize()).isEqualTo(10);
    }
}
