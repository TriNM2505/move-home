package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.dto.RouteEstimateResponse;
import vn.movehome.backend.dto.customer.PricingBreakdown;
import vn.movehome.backend.service.CommissionSettingsService;
import vn.movehome.backend.service.PricingService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho OrderService — tao don (createOrder), sinh order_code duy nhat,
 * tinh gia tri tien theo AC-08 (BigDecimal, lam tron HALF_UP scale 0).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OsrmClient osrmClient;

    @Mock
    private PricingService pricingService;

    @Mock
    private CommissionSettingsService commissionSettingsService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, osrmClient, pricingService, commissionSettingsService);
    }

    private CreateOrderRequest.Location pickupLocation() {
        return new CreateOrderRequest.Location(
                "123 Duong Lang, phuong Lang Thuong",
                "DONG_DA",
                new BigDecimal("21.0122345"),
                new BigDecimal("105.8234567"),
                2,
                true,
                false);
    }

    private CreateOrderRequest.Location dropoffLocation() {
        return new CreateOrderRequest.Location(
                "456 Nguyen Trai, phuong Thanh Xuan Trung",
                "THANH_XUAN",
                new BigDecimal("20.9987654"),
                new BigDecimal("105.8012345"),
                5,
                false,
                true);
    }

    private CreateOrderRequest baseRequest(String notes) {
        return new CreateOrderRequest(
                "TRUCK_1T",
                pickupLocation(),
                dropoffLocation(),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                2,
                notes);
    }

    @Test
    void createOrder_happyPath_buildsOrderWithComputedFieldsAndSavesIt() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = baseRequest("  Do co gia tri, xin nhe tay  ");

        RouteEstimateResponse route = new RouteEstimateResponse(new BigDecimal("12.34"), 45);
        when(osrmClient.calculateRoute(
                request.pickup().lat().doubleValue(),
                request.pickup().lng().doubleValue(),
                request.dropoff().lat().doubleValue(),
                request.dropoff().lng().doubleValue()))
                .thenReturn(route);

        PricingBreakdown pricing = new PricingBreakdown(
                new BigDecimal("370200.5"),
                new BigDecimal("111060.4"),
                new BigDecimal("200000"),
                new BigDecimal("100000"),
                new BigDecimal("600000"),
                new BigDecimal("1381260.9"));
        when(pricingService.calculate(
                eq("TRUCK_1T"),
                eq(route.distanceKm()),
                eq(request.scheduledAt().toInstant()),
                eq(request.pickup().hasAlley()),
                eq(request.dropoff().hasAlley()),
                eq(request.pickup().floor()),
                eq(request.pickup().hasElevator()),
                eq(request.dropoff().floor()),
                eq(request.dropoff().hasElevator()),
                eq(request.porterCount())))
                .thenReturn(pricing);

        when(commissionSettingsService.currentCommissionRate()).thenReturn(new BigDecimal("0.3000"));
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);

        ArgumentCaptor<ServiceOrder> captor = ArgumentCaptor.forClass(ServiceOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(invocation -> {
            ServiceOrder toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        CreateOrderResponse response = orderService.createOrder(customerId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.orderCode()).matches("^MH\\d{12}$");
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.totalQuote()).isEqualByComparingTo(new BigDecimal("1381261"));

        ServiceOrder saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getOrderCode()).matches("^MH\\d{12}$");
        assertThat(saved.getPickupAddress()).isEqualTo("123 Duong Lang, phuong Lang Thuong");
        assertThat(saved.getPickupDistrict()).isEqualTo("DONG_DA");
        assertThat(saved.getDropoffAddress()).isEqualTo("456 Nguyen Trai, phuong Thanh Xuan Trung");
        assertThat(saved.getDropoffDistrict()).isEqualTo("THANH_XUAN");
        assertThat(saved.getScheduledAt()).isEqualTo(request.scheduledAt());
        assertThat(saved.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(saved.getTotalQuote()).isEqualByComparingTo(new BigDecimal("1381261"));
        assertThat(saved.getCommissionRateSnapshot()).isEqualByComparingTo(new BigDecimal("0.3000"));
        assertThat(saved.getDistanceKm()).isEqualByComparingTo(new BigDecimal("12.34"));
        assertThat(saved.getEstimatedDurationMinutes()).isEqualTo(45);
        assertThat(saved.getNotes()).isEqualTo("Do co gia tri, xin nhe tay");
        assertThat(saved.getVehicleType()).isEqualTo("TRUCK_1T");
        assertThat(saved.getPorterCount()).isEqualTo(2);
        assertThat(saved.getPickupLat()).isEqualByComparingTo(request.pickup().lat());
        assertThat(saved.getPickupLng()).isEqualByComparingTo(request.pickup().lng());
        assertThat(saved.getDropoffLat()).isEqualByComparingTo(request.dropoff().lat());
        assertThat(saved.getDropoffLng()).isEqualByComparingTo(request.dropoff().lng());
        assertThat(saved.getPickupFloor()).isEqualTo(2);
        assertThat(saved.getPickupHasElevator()).isTrue();
        assertThat(saved.getPickupHasAlley()).isFalse();
        assertThat(saved.getDropoffFloor()).isEqualTo(5);
        assertThat(saved.getDropoffHasElevator()).isFalse();
        assertThat(saved.getDropoffHasAlley()).isTrue();
        // Money must be rounded HALF_UP scale 0 (AC-08)
        assertThat(saved.getBaseFare()).isEqualByComparingTo(new BigDecimal("370201"));
        assertThat(saved.getPeakSurcharge()).isEqualByComparingTo(new BigDecimal("111060"));
        assertThat(saved.getAlleySurcharge()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(saved.getFloorSurcharge()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(saved.getPorterFee()).isEqualByComparingTo(new BigDecimal("600000"));

        verify(orderRepository, times(1)).existsByOrderCode(anyString());
    }

    @Test
    void createOrder_notesBlank_normalizesToNull() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = baseRequest("     ");
        stubHappyPathCollaborators(request);

        ArgumentCaptor<ServiceOrder> captor = ArgumentCaptor.forClass(ServiceOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(invocation -> {
            ServiceOrder toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        orderService.createOrder(customerId, request);

        assertThat(captor.getValue().getNotes()).isNull();
    }

    @Test
    void createOrder_notesNull_staysNull() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = baseRequest(null);
        stubHappyPathCollaborators(request);

        ArgumentCaptor<ServiceOrder> captor = ArgumentCaptor.forClass(ServiceOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(invocation -> {
            ServiceOrder toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        orderService.createOrder(customerId, request);

        assertThat(captor.getValue().getNotes()).isNull();
    }

    @Test
    void createOrder_orderCodeCollision_retriesUntilUnique() {
        UUID customerId = UUID.randomUUID();
        CreateOrderRequest request = baseRequest("Ghi chu");
        stubHappyPathCollaborators(request);
        // Lan dau trung ma, lan hai moi hop le
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(true, false);

        when(orderRepository.save(org.mockito.ArgumentMatchers.any(ServiceOrder.class)))
                .thenAnswer(invocation -> {
                    ServiceOrder toSave = invocation.getArgument(0);
                    toSave.setId(UUID.randomUUID());
                    return toSave;
                });

        orderService.createOrder(customerId, request);

        verify(orderRepository, times(2)).existsByOrderCode(anyString());
    }

    private void stubHappyPathCollaborators(CreateOrderRequest request) {
        RouteEstimateResponse route = new RouteEstimateResponse(new BigDecimal("5.00"), 20);
        when(osrmClient.calculateRoute(
                request.pickup().lat().doubleValue(),
                request.pickup().lng().doubleValue(),
                request.dropoff().lat().doubleValue(),
                request.dropoff().lng().doubleValue()))
                .thenReturn(route);

        PricingBreakdown pricing = new PricingBreakdown(
                new BigDecimal("150000").setScale(0, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(0),
                BigDecimal.ZERO.setScale(0),
                BigDecimal.ZERO.setScale(0),
                new BigDecimal("600000"),
                new BigDecimal("750000"));
        when(pricingService.calculate(
                eq(request.vehicleType()),
                eq(route.distanceKm()),
                eq(request.scheduledAt().toInstant()),
                eq(request.pickup().hasAlley()),
                eq(request.dropoff().hasAlley()),
                eq(request.pickup().floor()),
                eq(request.pickup().hasElevator()),
                eq(request.dropoff().floor()),
                eq(request.dropoff().hasElevator()),
                eq(request.porterCount())))
                .thenReturn(pricing);

        when(commissionSettingsService.currentCommissionRate()).thenReturn(new BigDecimal("0.3000"));
    }
}
