package vn.movehome.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import vn.movehome.backend.dto.customer.PricingBreakdown;

@Service
public class PricingService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal PEAK_RATE = new BigDecimal("0.30");
    private static final BigDecimal ALLEY_SURCHARGE = new BigDecimal("200000");
    private static final BigDecimal FLOOR_SURCHARGE_PER_FLOOR = new BigDecimal("50000");
    private static final BigDecimal PORTER_FEE_PER_PERSON = new BigDecimal("300000");

    private static final Map<String, BigDecimal> RATE_PER_KM = Map.of(
            "TRUCK_500KG", new BigDecimal("15000"),
            "TRUCK_1T", new BigDecimal("30000"),
            "TRUCK_15T", new BigDecimal("40000")
    );

    public PricingBreakdown calculate(
            String vehicleType,
            BigDecimal distanceKm,
            Instant scheduledAt,
            boolean pickupHasAlley,
            boolean dropoffHasAlley,
            int pickupFloor,
            boolean pickupHasElevator,
            int dropoffFloor,
            boolean dropoffHasElevator,
            int porterCount) {

        BigDecimal baseFare = money(distanceKm.multiply(ratePerKm(vehicleType)));
        BigDecimal peakSurcharge = isPeakHour(scheduledAt)
                ? money(baseFare.multiply(PEAK_RATE))
                : zero();
        BigDecimal alleySurcharge = pickupHasAlley || dropoffHasAlley
                ? ALLEY_SURCHARGE
                : zero();
        BigDecimal floorSurcharge = money(FLOOR_SURCHARGE_PER_FLOOR.multiply(BigDecimal.valueOf(chargeableFloors(
                pickupFloor,
                pickupHasElevator,
                dropoffFloor,
                dropoffHasElevator))));
        BigDecimal porterFee = money(PORTER_FEE_PER_PERSON.multiply(BigDecimal.valueOf(porterCount)));
        BigDecimal totalQuote = money(baseFare
                .add(peakSurcharge)
                .add(alleySurcharge)
                .add(floorSurcharge)
                .add(porterFee));

        return new PricingBreakdown(
                baseFare,
                peakSurcharge,
                alleySurcharge,
                floorSurcharge,
                porterFee,
                totalQuote);
    }

    private BigDecimal ratePerKm(String vehicleType) {
        return RATE_PER_KM.getOrDefault(vehicleType.trim().toUpperCase(Locale.ROOT), RATE_PER_KM.get("TRUCK_500KG"));
    }

    private boolean isPeakHour(Instant scheduledAt) {
        LocalTime time = scheduledAt.atZone(VIETNAM_ZONE).toLocalTime();
        return isBetween(time, LocalTime.of(7, 0), LocalTime.of(9, 0))
                || isBetween(time, LocalTime.of(17, 0), LocalTime.of(19, 0));
    }

    private boolean isBetween(LocalTime time, LocalTime startInclusive, LocalTime endExclusive) {
        return !time.isBefore(startInclusive) && time.isBefore(endExclusive);
    }

    private int chargeableFloors(int pickupFloor, boolean pickupHasElevator, int dropoffFloor, boolean dropoffHasElevator) {
        int highestFloor = Math.max(
                effectiveFloor(pickupFloor, pickupHasElevator),
                effectiveFloor(dropoffFloor, dropoffHasElevator));

        return Math.max(0, highestFloor - 3);
    }

    private int effectiveFloor(int floor, boolean hasElevator) {
        return hasElevator ? 0 : floor;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(0);
    }
}
