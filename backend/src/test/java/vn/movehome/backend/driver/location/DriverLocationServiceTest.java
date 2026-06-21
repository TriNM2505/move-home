package vn.movehome.backend.driver.location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverLocationServiceTest {

    @Mock
    private DriverLocationRepository driverLocationRepository;

    @Mock
    private OrderRepository orderRepository;

    private DriverLocationService service;

    @BeforeEach
    void setUp() {
        service = new DriverLocationService(driverLocationRepository, orderRepository);
    }

    @Test
    void updateLocationUpsertsDriverAndCurrentInProgressOrder() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UpdateDriverLocationRequest request = request();
        ServiceOrder order = ServiceOrder.builder().id(orderId).build();

        when(orderRepository.findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                driverId, "IN_PROGRESS"))
                .thenReturn(Optional.of(order));
        when(driverLocationRepository.upsertLatest(
                driverId,
                orderId,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speedKmh()))
                .thenReturn(1);

        service.updateLocation(driverId, request);

        verify(driverLocationRepository).upsertLatest(
                driverId,
                orderId,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speedKmh());
    }

    @Test
    void updateLocationLeavesCurrentOrderNullWithoutInProgressOrder() {
        UUID driverId = UUID.randomUUID();
        UpdateDriverLocationRequest request = request();

        when(orderRepository.findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                driverId, "IN_PROGRESS"))
                .thenReturn(Optional.empty());
        when(driverLocationRepository.upsertLatest(
                driverId,
                null,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speedKmh()))
                .thenReturn(1);

        service.updateLocation(driverId, request);

        verify(driverLocationRepository).upsertLatest(
                driverId,
                null,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speedKmh());
    }

    @Test
    void updateLocationRejectsMoreThanOneWritePerTenSeconds() {
        UUID driverId = UUID.randomUUID();
        UpdateDriverLocationRequest request = request();

        when(orderRepository.findFirstByDriverIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                driverId, "IN_PROGRESS"))
                .thenReturn(Optional.empty());
        when(driverLocationRepository.upsertLatest(
                driverId,
                null,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speedKmh()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updateLocation(driverId, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getReason()).startsWith("RATE_LIMITED|");
                });
    }

    @Test
    void getOrderLocationReturnsLatestDriverLocation() {
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant recordedAt = Instant.now().minusSeconds(5);
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(customerId)
                .driverId(driverId)
                .status("IN_PROGRESS")
                .build();
        DriverLocation location = DriverLocation.builder()
                .driverId(driverId)
                .currentOrderId(orderId)
                .lat(new BigDecimal("21.0285110"))
                .lng(new BigDecimal("105.8048170"))
                .heading(new BigDecimal("120.50"))
                .speedKmh(new BigDecimal("24.20"))
                .recordedAt(recordedAt)
                .build();

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(driverLocationRepository.findById(driverId)).thenReturn(Optional.of(location));

        DriverLocationResponse response = service.getOrderLocation(customerId, orderId);

        assertThat(response.driverId()).isEqualTo(driverId);
        assertThat(response.lat()).isEqualByComparingTo("21.0285110");
        assertThat(response.lng()).isEqualByComparingTo("105.8048170");
        assertThat(response.heading()).isEqualByComparingTo("120.50");
        assertThat(response.speedKmh()).isEqualByComparingTo("24.20");
        assertThat(response.recordedAt()).isEqualTo(recordedAt);
        assertThat(response.stale()).isFalse();
    }

    @Test
    void getOrderLocationRejectsAnotherCustomersOrderWithoutReadingLocation() {
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .customerId(UUID.randomUUID())
                .driverId(driverId)
                .status("IN_PROGRESS")
                .build();

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrderLocation(customerId, orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getReason()).startsWith("ORDER_OWNERSHIP_REQUIRED|");
                });
        verify(driverLocationRepository, never()).findById(driverId);
    }

    private UpdateDriverLocationRequest request() {
        return new UpdateDriverLocationRequest(
                new BigDecimal("21.0285110"),
                new BigDecimal("105.8048170"),
                new BigDecimal("120.50"),
                new BigDecimal("24.20"));
    }
}
