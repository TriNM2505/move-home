package vn.movehome.backend.driver.location;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DriverLocationResponse(
        @JsonProperty("driver_id") UUID driverId,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal heading,
        @JsonProperty("speed_kmh") BigDecimal speedKmh,
        @JsonProperty("recorded_at") Instant recordedAt,
        boolean stale
) {
}
