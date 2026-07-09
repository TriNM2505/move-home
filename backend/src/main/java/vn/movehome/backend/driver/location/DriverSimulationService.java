package vn.movehome.backend.driver.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mo phong vi tri tai xe di chuyen (khong dung GPS that).
 * Moi 1 phut: voi don ACCEPTED (dang den diem don) hoac IN_PROGRESS (dang giao),
 * tai xe tien dan doc tuyen duong that OSRM o toc do 30 km/h, ghi vao driver_location.
 * Khach poll /api/customer/orders/{id}/location de thay marker nhich dan.
 */
@Service
public class DriverSimulationService {

    private static final Logger log = LoggerFactory.getLogger(DriverSimulationService.class);

    private static final double SPEED_KMH = 30.0;
    // Cap nhat moi 15 giay cho muot (van giu 30 km/h): 30 * 15/3600 = 0.125 km moi lan
    private static final long TICK_MS = 15_000L;
    private static final double STEP_KM = SPEED_KMH * (TICK_MS / 1000.0) / 3600.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;
    // Diem xuat phat gia cua tai xe khi moi nhan don (~4-5km tu diem don) de co doan "dang den don"
    private static final double PICKUP_ORIGIN_OFFSET_DEG = 0.03;
    private static final Set<String> ACTIVE_STATUSES = Set.of("ACCEPTED", "IN_PROGRESS");

    private final OrderRepository orderRepository;
    private final OsrmClient osrmClient;
    private final DriverLocationRepository driverLocationRepository;
    private final TransactionTemplate transactionTemplate;

    // Trang thai mo phong theo don (in-memory; mat khi restart → tu khoi tao lai tu dau)
    private final Map<UUID, Sim> sims = new ConcurrentHashMap<>();

    public DriverSimulationService(
            OrderRepository orderRepository,
            OsrmClient osrmClient,
            DriverLocationRepository driverLocationRepository,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.osrmClient = osrmClient;
        this.driverLocationRepository = driverLocationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedRate = TICK_MS)
    public void tick() {
        List<ServiceOrder> orders;
        try {
            orders = orderRepository.findByStatusInAndDeletedAtIsNull(ACTIVE_STATUSES);
        } catch (RuntimeException ex) {
            log.warn("Mo phong tai xe: khong tai duoc danh sach don.", ex);
            return;
        }

        Set<UUID> activeIds = new HashSet<>();
        for (ServiceOrder order : orders) {
            if (order.getDriverId() == null
                    || order.getPickupLat() == null || order.getPickupLng() == null
                    || order.getDropoffLat() == null || order.getDropoffLng() == null) {
                continue;
            }
            activeIds.add(order.getId());
            try {
                // Moi don 1 transaction rieng → 1 don loi khong anh huong don khac
                transactionTemplate.executeWithoutResult(status -> advance(order));
            } catch (RuntimeException ex) {
                log.error("Mo phong tai xe: LOI khi cap nhat don {}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
        if (!activeIds.isEmpty()) {
            log.info("Mo phong tai xe: da cap nhat vi tri cho {} don dang giao.", activeIds.size());
        }
        // Don da xong → bo trang thai mo phong
        sims.keySet().retainAll(activeIds);
    }

    /**
     * Khi tai xe nhan don (→ ACCEPTED, sau commit), ghi vi tri ban dau ngay lap tuc.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAccepted(OrderStatusChangedEvent event) {
        if (!"ACCEPTED".equals(event.newStatus())) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status ->
                    orderRepository.findById(event.orderId()).ifPresent(this::seedInitialLocation));
        } catch (RuntimeException ex) {
            log.warn("Seed vi tri sau khi nhan don that bai: {}", event.orderId(), ex);
        }
    }

    /**
     * Ghi vi tri tai xe NGAY khi nhan don (tai diem xuat phat gia) de khach thay lien,
     * khong phai cho toi tick dau (~1 phut). Khong goi OSRM de khong lam cham API nhan don.
     */
    public void seedInitialLocation(ServiceOrder order) {
        if (order.getDriverId() == null || order.getPickupLat() == null || order.getPickupLng() == null) {
            return;
        }
        try {
            double lat = order.getPickupLat().doubleValue() + PICKUP_ORIGIN_OFFSET_DEG;
            double lng = order.getPickupLng().doubleValue() + PICKUP_ORIGIN_OFFSET_DEG;
            int affected = driverLocationRepository.upsertLatest(
                    order.getDriverId(), order.getId(),
                    scale7(lat), scale7(lng), null,
                    BigDecimal.valueOf(SPEED_KMH).setScale(2, RoundingMode.HALF_UP));
            log.info("Seed vi tri: don={} tai_xe={} affected={}",
                    order.getId(), order.getDriverId(), affected);
        } catch (RuntimeException ex) {
            log.error("Seed vi tri tai xe LOI cho don {}: {}", order.getId(), ex.getMessage(), ex);
        }
    }

    private void advance(ServiceOrder order) {
        String phase = order.getStatus();
        Sim sim = sims.get(order.getId());
        if (sim == null || !sim.phase.equals(phase)) {
            sim = buildSim(order, phase);
            sims.put(order.getId(), sim);
        }

        sim.traveledKm = Math.min(sim.totalKm, sim.traveledKm + STEP_KM);
        double[] pos = interpolate(sim);

        int affected = driverLocationRepository.upsertLatest(
                order.getDriverId(),
                order.getId(),
                scale7(pos[0]),
                scale7(pos[1]),
                null,
                BigDecimal.valueOf(SPEED_KMH).setScale(2, RoundingMode.HALF_UP));
        log.info("Mo phong GHI: don={} tai_xe={} pos=({},{}) affected={}",
                order.getId(), order.getDriverId(), pos[0], pos[1], affected);
    }

    private Sim buildSim(ServiceOrder order, String phase) {
        double pickupLat = order.getPickupLat().doubleValue();
        double pickupLng = order.getPickupLng().doubleValue();
        double dropoffLat = order.getDropoffLat().doubleValue();
        double dropoffLng = order.getDropoffLng().doubleValue();

        double startLat, startLng, endLat, endLng;
        if ("ACCEPTED".equals(phase)) {
            // Dang den diem don: tu diem xuat phat gia → diem don
            startLat = pickupLat + PICKUP_ORIGIN_OFFSET_DEG;
            startLng = pickupLng + PICKUP_ORIGIN_OFFSET_DEG;
            endLat = pickupLat;
            endLng = pickupLng;
        } else {
            // IN_PROGRESS: diem don → diem tra
            startLat = pickupLat;
            startLng = pickupLng;
            endLat = dropoffLat;
            endLng = dropoffLng;
        }

        List<double[]> points = osrmClient.fetchRouteGeometry(startLat, startLng, endLat, endLng);

        Sim sim = new Sim();
        sim.phase = phase;
        sim.points = points;
        sim.cumulativeKm = new double[points.size()];
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineKm(points.get(i - 1)[0], points.get(i - 1)[1], points.get(i)[0], points.get(i)[1]);
            sim.cumulativeKm[i] = total;
        }
        sim.totalKm = total;
        sim.traveledKm = 0;
        return sim;
    }

    private double[] interpolate(Sim sim) {
        if (sim.points.size() == 1 || sim.totalKm <= 0) {
            return sim.points.get(sim.points.size() - 1);
        }
        double d = sim.traveledKm;
        for (int i = 1; i < sim.points.size(); i++) {
            if (d <= sim.cumulativeKm[i]) {
                double segStart = sim.cumulativeKm[i - 1];
                double segEnd = sim.cumulativeKm[i];
                double f = segEnd > segStart ? (d - segStart) / (segEnd - segStart) : 0;
                double[] a = sim.points.get(i - 1);
                double[] b = sim.points.get(i);
                return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
            }
        }
        return sim.points.get(sim.points.size() - 1);
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

    private static final class Sim {
        String phase;
        List<double[]> points;
        double[] cumulativeKm;
        double totalKm;
        double traveledKm;
    }
}
