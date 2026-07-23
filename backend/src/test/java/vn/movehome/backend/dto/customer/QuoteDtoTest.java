package vn.movehome.backend.dto.customer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteDtoTest {

    @Test
    void quoteRequestAndLocationExposeAllFields() {
        QuoteRequest.Location pickup = new QuoteRequest.Location(
                "123 Lang Ha", "Dong Da", new BigDecimal("21.01"), new BigDecimal("105.81"), 2, true, true);
        QuoteRequest.Location dropoff = new QuoteRequest.Location(
                "456 Cau Giay", "Cau Giay", new BigDecimal("21.03"), new BigDecimal("105.79"), 0, false, false);
        Instant scheduledAt = Instant.parse("2026-06-19T07:00:00Z");
        QuoteRequest request = new QuoteRequest("TRUCK_500KG", pickup, dropoff, scheduledAt, 2);

        assertThat(request.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(request.pickup()).isEqualTo(pickup);
        assertThat(request.dropoff()).isEqualTo(dropoff);
        assertThat(request.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(request.porterCount()).isEqualTo(2);

        assertThat(pickup.address()).isEqualTo("123 Lang Ha");
        assertThat(pickup.district()).isEqualTo("Dong Da");
        assertThat(pickup.lat()).isEqualTo(new BigDecimal("21.01"));
        assertThat(pickup.lng()).isEqualTo(new BigDecimal("105.81"));
        assertThat(pickup.floor()).isEqualTo(2);
        assertThat(pickup.hasElevator()).isTrue();
        assertThat(pickup.hasAlley()).isTrue();

        assertThat(dropoff.floor()).isEqualTo(0);
        assertThat(dropoff.hasElevator()).isFalse();
        assertThat(dropoff.hasAlley()).isFalse();
    }

    @Test
    void quoteResponseExposesAllFields() {
        QuoteResponse response = new QuoteResponse(
                new BigDecimal("10.00"), 20,
                new BigDecimal("150000"), new BigDecimal("45000"), new BigDecimal("200000"),
                new BigDecimal("100000"), new BigDecimal("600000"), new BigDecimal("1095000"));

        assertThat(response.distanceKm()).isEqualTo(new BigDecimal("10.00"));
        assertThat(response.durationMinutes()).isEqualTo(20);
        assertThat(response.baseFare()).isEqualTo(new BigDecimal("150000"));
        assertThat(response.peakSurcharge()).isEqualTo(new BigDecimal("45000"));
        assertThat(response.alleySurcharge()).isEqualTo(new BigDecimal("200000"));
        assertThat(response.floorSurcharge()).isEqualTo(new BigDecimal("100000"));
        assertThat(response.porterFee()).isEqualTo(new BigDecimal("600000"));
        assertThat(response.totalQuote()).isEqualTo(new BigDecimal("1095000"));
    }
}
