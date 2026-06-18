package vn.movehome.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import vn.movehome.backend.dto.customer.PricingBreakdown;

class PricingServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PricingService pricingService = new PricingService();

    @Test
    void calculatesBaseFareOnlyWhenNoSurchargeApplies() {
        PricingBreakdown result = calculate(
                "TRUCK_500KG",
                "10",
                localTime(14, 0),
                false,
                false,
                1,
                false,
                1,
                false,
                0);

        assertMoney(result.baseFare(), "150000");
        assertMoney(result.totalQuote(), "150000");
    }

    @Test
    void appliesPeakSurchargeAtEightAmVietnamTime() {
        PricingBreakdown result = calculate(
                "TRUCK_500KG",
                "10",
                localTime(8, 0),
                false,
                false,
                1,
                false,
                1,
                false,
                0);

        assertMoney(result.peakSurcharge(), "45000");
    }

    @Test
    void appliesFixedAlleySurchargeWhenPickupHasAlley() {
        PricingBreakdown result = calculate(
                "TRUCK_500KG",
                "10",
                localTime(14, 0),
                true,
                false,
                1,
                false,
                1,
                false,
                0);

        assertMoney(result.alleySurcharge(), "200000");
    }

    @Test
    void appliesFloorSurchargeForDropoffFloorAboveThirdWithoutElevator() {
        PricingBreakdown result = calculate(
                "TRUCK_500KG",
                "10",
                localTime(14, 0),
                false,
                false,
                1,
                true,
                5,
                false,
                0);

        assertMoney(result.floorSurcharge(), "100000");
    }

    @Test
    void appliesPorterFeeByPorterCount() {
        PricingBreakdown result = calculate(
                "TRUCK_500KG",
                "10",
                localTime(14, 0),
                false,
                false,
                1,
                false,
                1,
                false,
                2);

        assertMoney(result.porterFee(), "600000");
    }

    @Test
    void sumsMultipleSurchargesIntoTotalQuote() {
        PricingBreakdown result = calculate(
                "TRUCK_500KG",
                "10",
                localTime(8, 0),
                true,
                false,
                1,
                true,
                5,
                false,
                2);

        assertMoney(result.baseFare(), "150000");
        assertMoney(result.peakSurcharge(), "45000");
        assertMoney(result.alleySurcharge(), "200000");
        assertMoney(result.floorSurcharge(), "100000");
        assertMoney(result.porterFee(), "600000");
        assertMoney(result.totalQuote(), "1095000");
    }

    private PricingBreakdown calculate(
            String vehicleType,
            String distanceKm,
            java.time.Instant scheduledAt,
            boolean pickupHasAlley,
            boolean dropoffHasAlley,
            int pickupFloor,
            boolean pickupHasElevator,
            int dropoffFloor,
            boolean dropoffHasElevator,
            int porterCount) {
        return pricingService.calculate(
                vehicleType,
                new BigDecimal(distanceKm),
                scheduledAt,
                pickupHasAlley,
                dropoffHasAlley,
                pickupFloor,
                pickupHasElevator,
                dropoffFloor,
                dropoffHasElevator,
                porterCount);
    }

    private java.time.Instant localTime(int hour, int minute) {
        return LocalDateTime.of(2026, 6, 19, hour, minute)
                .atZone(VIETNAM_ZONE)
                .toInstant();
    }

    private void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual.compareTo(new BigDecimal(expected))).isZero();
    }
}
