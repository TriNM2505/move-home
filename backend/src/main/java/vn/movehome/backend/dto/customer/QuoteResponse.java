package vn.movehome.backend.dto.customer;

import java.math.BigDecimal;

public record QuoteResponse(
        BigDecimal distanceKm,
        int durationMinutes,
        BigDecimal baseFare,
        BigDecimal peakSurcharge,
        BigDecimal alleySurcharge,
        BigDecimal floorSurcharge,
        BigDecimal porterFee,
        BigDecimal totalQuote
) {
}
