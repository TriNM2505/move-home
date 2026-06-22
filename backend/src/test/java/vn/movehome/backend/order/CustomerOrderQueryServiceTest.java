package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private CustomerOrderQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new CustomerOrderQueryService(orderRepository);
    }

    @Test
    void getOrdersReturnsOnlyCustomerOrdersWithStablePagination() {
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(customerId);
        when(orderRepository.findByCustomerIdAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderListItemDTO> result = queryService.getOrders(customerId, null, 1, 20);

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(order.getId());
            assertThat(item.getPickupAddress()).isEqualTo(order.getPickupAddress());
            assertThat(item.getDropoffAddress()).isEqualTo(order.getDropoffAddress());
            assertThat(item.getVehicleType()).isEqualTo(order.getVehicleType());
            assertThat(item.getTotalQuote()).isEqualByComparingTo(order.getTotalQuote());
        });

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByCustomerIdAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(customerId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void getOrdersNormalizesAndAppliesStatusFilter() {
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByCustomerIdAndStatusAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.eq("COMPLETED"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(Page.empty());

        queryService.getOrders(customerId, " completed ", 0, 10);

        verify(orderRepository).findByCustomerIdAndStatusAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.eq("COMPLETED"),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void getOrdersRejectsUnsupportedStatusBeforeQuerying() {
        assertThatThrownBy(() -> queryService.getOrders(UUID.randomUUID(), "UNKNOWN", 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).startsWith("VALIDATION_ERROR|");
                });

        verifyNoInteractions(orderRepository);
    }

    @Test
    void getOrdersRejectsInvalidPaginationBeforeQuerying() {
        assertThatThrownBy(() -> queryService.getOrders(UUID.randomUUID(), null, -1, 101))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verifyNoInteractions(orderRepository);
    }

    @Test
    void getOrderDetailReturnsOwnedOrder() {
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = order(customerId);
        when(orderRepository.findByIdAndDeletedAtIsNull(order.getId())).thenReturn(Optional.of(order));

        OrderDetailDTO result = queryService.getOrderDetail(customerId, order.getId());

        assertThat(result.getId()).isEqualTo(order.getId());
        assertThat(result.getOrderCode()).isEqualTo(order.getOrderCode());
        assertThat(result.getTotalQuote()).isEqualByComparingTo(order.getTotalQuote());
    }

    @Test
    void getOrderDetailRejectsDifferentCustomerWithForbidden() {
        ServiceOrder order = order(UUID.randomUUID());
        when(orderRepository.findByIdAndDeletedAtIsNull(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> queryService.getOrderDetail(UUID.randomUUID(), order.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getReason()).startsWith("ORDER_OWNERSHIP_REQUIRED|");
                });
    }

    @Test
    void getOrderDetailReturnsNotFoundForMissingOrDeletedOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getOrderDetail(UUID.randomUUID(), orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    private ServiceOrder order(UUID customerId) {
        return ServiceOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("MH202606230001")
                .customerId(customerId)
                .status("COMPLETED")
                .vehicleType("TRUCK_1T")
                .pickupAddress("1 Phố Huế")
                .pickupDistrict("Hai Bà Trưng")
                .dropoffAddress("2 Nguyễn Trãi")
                .dropoffDistrict("Thanh Xuân")
                .scheduledAt(OffsetDateTime.parse("2026-06-24T08:00:00+07:00"))
                .totalQuote(new BigDecimal("940000"))
                .createdAt(OffsetDateTime.parse("2026-06-23T02:00:00Z"))
                .build();
    }
}
