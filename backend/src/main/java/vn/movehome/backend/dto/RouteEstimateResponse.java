package vn.movehome.backend.dto;

import java.math.BigDecimal;

public record RouteEstimateResponse(
        BigDecimal distanceKm,
        int durationMinutes
) {
}
