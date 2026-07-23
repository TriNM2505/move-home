package vn.movehome.backend.driver.location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test cho DriverSimulationService — mo phong vi tri tai xe di chuyen.
 * Khong goi that OSRM: OsrmClient duoc mock hoan toan.
 */
@ExtendWith(MockitoExtension.class)
class DriverSimulationServiceTest {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double STEP_KM = 30.0 * (15_000L / 1000.0) / 3600.0; // 0.125 km / tick
    private static final BigDecimal SPEED_BD = BigDecimal.valueOf(30.0).setScale(2, RoundingMode.HALF_UP);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OsrmClient osrmClient;

    @Mock
    private DriverLocationRepository driverLocationRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private DriverSimulationService service;

    @BeforeEach
    void setUp() {
        // Dung cho moi lan TransactionTemplate.executeWithoutResult(...) chay callback that.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new DriverSimulationService(orderRepository, osrmClient, driverLocationRepository, transactionManager);
    }

    // ----------------------------------------------------------------
    // tick(): dieu kien bo qua don (driver/pickup/dropoff con thieu toa do)
    // ----------------------------------------------------------------

    @Test
    void tickSkipsOrderWithoutDriverAssigned() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), null, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        service.tick();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    @Test
    void tickSkipsOrderMissingPickupLat() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), "ACCEPTED",
                null, bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        service.tick();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    @Test
    void tickSkipsOrderMissingPickupLng() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), "ACCEPTED",
                bd(21.0), null, bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        service.tick();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    @Test
    void tickSkipsOrderMissingDropoffLat() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), "IN_PROGRESS",
                bd(21.0), bd(105.0), null, bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        service.tick();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    @Test
    void tickSkipsOrderMissingDropoffLng() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), "IN_PROGRESS",
                bd(21.0), bd(105.0), bd(21.01), null);
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        service.tick();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    @Test
    void tickWithNoActiveOrdersDoesNothing() {
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of());

        service.tick();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    @Test
    void tickWhenOrderLookupThrowsLogsAndReturnsWithoutPropagating() {
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.tick()).doesNotThrowAnyException();

        verifyNoInteractions(osrmClient, driverLocationRepository);
    }

    // ----------------------------------------------------------------
    // tick(): tien trinh mo phong hop le
    // ----------------------------------------------------------------

    @Test
    void tickAdvancesAcceptedOrderTowardPickupPoint() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        // Tinh giong het cong thuc production (pickupLat + OFFSET) de dam bao khop bit-for-bit
        // khi so sanh double chinh xac trong verify() ben duoi (khong qua lam tron BigDecimal).
        double pickupLatVal = 21.0;
        double pickupLngVal = 105.0;
        double startLat = pickupLatVal + 0.03;
        double startLng = pickupLngVal + 0.03;
        double endLat = pickupLatVal;
        double endLng = pickupLngVal;
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{startLat, startLng}, new double[]{endLat, endLng}));

        service.tick();

        double total = haversineKm(startLat, startLng, endLat, endLng);
        double f = STEP_KM / total;
        double expectedLat = startLat + (endLat - startLat) * f;
        double expectedLng = startLng + (endLng - startLng) * f;

        verify(osrmClient).fetchRouteGeometry(startLat, startLng, endLat, endLng);
        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(expectedLat)), eq(scale7(expectedLng)), isNull(), eq(SPEED_BD));
    }

    @Test
    void tickAdvancesInProgressOrderTowardDropoffPoint() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "IN_PROGRESS",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        double startLat = 21.0;
        double startLng = 105.0;
        double endLat = 21.01;
        double endLng = 105.01;
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{startLat, startLng}, new double[]{endLat, endLng}));

        service.tick();

        double total = haversineKm(startLat, startLng, endLat, endLng);
        double f = STEP_KM / total;
        double expectedLat = startLat + (endLat - startLat) * f;
        double expectedLng = startLng + (endLng - startLng) * f;

        verify(osrmClient).fetchRouteGeometry(startLat, startLng, endLat, endLng);
        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(expectedLat)), eq(scale7(expectedLng)), isNull(), eq(SPEED_BD));
    }

    @Test
    void tickReusesBuiltRouteWhenPhaseUnchangedAcrossTicks() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{21.03, 105.03}, new double[]{21.0, 105.0}));

        service.tick();
        service.tick();

        verify(osrmClient, times(1)).fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        ArgumentCaptor<BigDecimal> latCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(driverLocationRepository, times(2)).upsertLatest(
                eq(driverId), eq(orderId), latCaptor.capture(), any(BigDecimal.class), isNull(), eq(SPEED_BD));

        List<BigDecimal> lats = latCaptor.getAllValues();
        assertThat(lats).hasSize(2);
        // Dang tien tu 21.03 ve 21.0 → lan 2 phai gan pickup hon (lat nho hon) lan 1.
        assertThat(lats.get(1).doubleValue()).isLessThan(lats.get(0).doubleValue());
    }

    @Test
    void tickRebuildsRouteWhenOrderPhaseChangesFromAcceptedToInProgress() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder acceptedOrder = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        ServiceOrder inProgressOrder = buildOrder(orderId, driverId, "IN_PROGRESS",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(acceptedOrder))
                .thenReturn(List.of(inProgressOrder));
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{21.03, 105.03}, new double[]{21.0, 105.0}));

        service.tick();
        service.tick();

        verify(osrmClient, times(2)).fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void tickRebuildsRouteAfterOrderTemporarilyLeavesActiveSet() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(order))
                .thenReturn(List.of())
                .thenReturn(List.of(order));
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{21.03, 105.03}, new double[]{21.0, 105.0}));

        service.tick(); // xay dung sim
        service.tick(); // don khong con active → xoa sim
        service.tick(); // don xuat hien lai → phai build lai sim

        verify(osrmClient, times(2)).fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void tickUsesSinglePointDirectlyWhenRouteHasOnlyOnePoint() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{21.02, 105.02}));

        service.tick();

        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(21.02)), eq(scale7(105.02)), isNull(), eq(SPEED_BD));
    }

    @Test
    void tickStaysAtSamePointWhenRouteHasZeroDistance() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{21.05, 105.05}, new double[]{21.05, 105.05}));

        service.tick();

        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(21.05)), eq(scale7(105.05)), isNull(), eq(SPEED_BD));
    }

    @Test
    void tickAdvancesPastShortFirstSegmentIntoSecondSegment() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order));

        double[] p0 = {21.0000000, 105.0};
        double[] p1 = {21.0005000, 105.0}; // doan ngan hon STEP_KM (~0.0556 km)
        double[] p2 = {21.0105000, 105.0}; // doan tiep theo dai hon nhieu
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(p0, p1, p2));

        service.tick();

        double seg1 = haversineKm(p0[0], p0[1], p1[0], p1[1]);
        double seg2 = haversineKm(p1[0], p1[1], p2[0], p2[1]);
        assertThat(seg1).isLessThan(STEP_KM);
        assertThat(seg1 + seg2).isGreaterThan(STEP_KM);

        double f = (STEP_KM - seg1) / seg2;
        double expectedLat = p1[0] + (p2[0] - p1[0]) * f;
        double expectedLng = p1[1] + (p2[1] - p1[1]) * f;

        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(expectedLat)), eq(scale7(expectedLng)), isNull(), eq(SPEED_BD));
    }

    @Test
    void tickContinuesRemainingOrdersWhenOneOrderAdvanceFails() {
        UUID driverId1 = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        UUID driverId2 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        ServiceOrder order1 = buildOrder(orderId1, driverId1, "ACCEPTED",
                bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        ServiceOrder order2 = buildOrder(orderId2, driverId2, "ACCEPTED",
                bd(22.0), bd(106.0), bd(22.01), bd(106.01));
        when(orderRepository.findByStatusInAndDeletedAtIsNull(any())).thenReturn(List.of(order1, order2));
        when(osrmClient.fetchRouteGeometry(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[]{21.03, 105.03}, new double[]{21.0, 105.0}));
        when(driverLocationRepository.upsertLatest(eq(driverId1), eq(orderId1), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("ghi loi cho don 1"));

        assertThatCode(() -> service.tick()).doesNotThrowAnyException();

        verify(driverLocationRepository).upsertLatest(eq(driverId1), eq(orderId1), any(), any(), any(), any());
        verify(driverLocationRepository).upsertLatest(eq(driverId2), eq(orderId2), any(), any(), any(), any());
    }

    // ----------------------------------------------------------------
    // onOrderAccepted(...)
    // ----------------------------------------------------------------

    @Test
    void onOrderAcceptedIgnoresEventsForOtherStatuses() {
        OrderStatusChangedEvent event = statusEvent(UUID.randomUUID(), "CONFIRMED", "IN_PROGRESS");

        service.onOrderAccepted(event);

        verifyNoInteractions(orderRepository, driverLocationRepository);
    }

    @Test
    void onOrderAcceptedSeedsInitialLocationWhenOrderFound() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED", bd(21.0), bd(105.0), bd(21.01), bd(105.01));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        OrderStatusChangedEvent event = statusEvent(orderId, "ASSIGNED", "ACCEPTED");

        service.onOrderAccepted(event);

        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(21.03)), eq(scale7(105.03)), isNull(), eq(SPEED_BD));
    }

    @Test
    void onOrderAcceptedDoesNothingWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        OrderStatusChangedEvent event = statusEvent(orderId, "ASSIGNED", "ACCEPTED");

        service.onOrderAccepted(event);

        verifyNoInteractions(driverLocationRepository);
    }

    @Test
    void onOrderAcceptedSwallowsExceptionFromLookup() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenThrow(new RuntimeException("db down"));
        OrderStatusChangedEvent event = statusEvent(orderId, "ASSIGNED", "ACCEPTED");

        assertThatCode(() -> service.onOrderAccepted(event)).doesNotThrowAnyException();

        verifyNoInteractions(driverLocationRepository);
    }

    // ----------------------------------------------------------------
    // seedInitialLocation(...)
    // ----------------------------------------------------------------

    @Test
    void seedInitialLocationSkipsWhenDriverIdNull() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), null, "ACCEPTED", bd(21.0), bd(105.0), null, null);

        service.seedInitialLocation(order);

        verifyNoInteractions(driverLocationRepository);
    }

    @Test
    void seedInitialLocationSkipsWhenPickupLatNull() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), "ACCEPTED", null, bd(105.0), null, null);

        service.seedInitialLocation(order);

        verifyNoInteractions(driverLocationRepository);
    }

    @Test
    void seedInitialLocationSkipsWhenPickupLngNull() {
        ServiceOrder order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), "ACCEPTED", bd(21.0), null, null, null);

        service.seedInitialLocation(order);

        verifyNoInteractions(driverLocationRepository);
    }

    @Test
    void seedInitialLocationWritesOffsetSeedPosition() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED", bd(21.0), bd(105.0), null, null);

        service.seedInitialLocation(order);

        verify(driverLocationRepository).upsertLatest(
                eq(driverId), eq(orderId), eq(scale7(21.03)), eq(scale7(105.03)), isNull(), eq(SPEED_BD));
    }

    @Test
    void seedInitialLocationSwallowsRepositoryException() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = buildOrder(orderId, driverId, "ACCEPTED", bd(21.0), bd(105.0), null, null);
        when(driverLocationRepository.upsertLatest(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> service.seedInitialLocation(order)).doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private ServiceOrder buildOrder(UUID id, UUID driverId, String status,
                                     BigDecimal pickupLat, BigDecimal pickupLng,
                                     BigDecimal dropoffLat, BigDecimal dropoffLng) {
        return ServiceOrder.builder()
                .id(id)
                .driverId(driverId)
                .status(status)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropoffLat(dropoffLat)
                .dropoffLng(dropoffLng)
                .build();
    }

    private OrderStatusChangedEvent statusEvent(UUID orderId, String oldStatus, String newStatus) {
        return new OrderStatusChangedEvent(
                orderId,
                "MH20260722ABCD",
                oldStatus,
                newStatus,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SYSTEM",
                OffsetDateTime.now());
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    private BigDecimal scale7(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ----------------------------------------------------------------
    // interpolate(): nhanh fallback cuoi vong lap (khong reachable qua advance()/tick() vi
    // traveledKm luon duoc cap boi Math.min(totalKm, ...) trung khop voi cumulativeKm cuoi cung).
    // Dung reflection de dung truc tiep Sim + interpolate() va phu nhanh du phong nay.
    // ----------------------------------------------------------------

    @Test
    void interpolate_travelledBeyondLastCumulativeSegment_returnsLastPoint() throws Exception {
        Class<?> simClass = Class.forName(
                "vn.movehome.backend.driver.location.DriverSimulationService$Sim");
        java.lang.reflect.Constructor<?> simCtor = simClass.getDeclaredConstructor();
        simCtor.setAccessible(true);
        Object sim = simCtor.newInstance();

        double[] p0 = {21.0, 105.8};
        double[] p1 = {21.1, 105.9};
        List<double[]> points = List.of(p0, p1);

        java.lang.reflect.Field pointsField = simClass.getDeclaredField("points");
        pointsField.setAccessible(true);
        pointsField.set(sim, points);

        java.lang.reflect.Field cumulativeKmField = simClass.getDeclaredField("cumulativeKm");
        cumulativeKmField.setAccessible(true);
        cumulativeKmField.set(sim, new double[]{0.0, 5.0});

        java.lang.reflect.Field totalKmField = simClass.getDeclaredField("totalKm");
        totalKmField.setAccessible(true);
        totalKmField.set(sim, 5.0);

        java.lang.reflect.Field traveledKmField = simClass.getDeclaredField("traveledKm");
        traveledKmField.setAccessible(true);
        // Vuot qua doan cuoi cung (cumulativeKm[last] = 5.0) -> vong lap khong khop, roi vao fallback cuoi ham.
        traveledKmField.set(sim, 999.0);

        java.lang.reflect.Method interpolate = DriverSimulationService.class
                .getDeclaredMethod("interpolate", simClass);
        interpolate.setAccessible(true);

        double[] result = (double[]) interpolate.invoke(service, sim);

        assertThat(result).isEqualTo(p1);
    }
}
