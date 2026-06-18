package vn.movehome.backend.dto.customer;

import java.math.BigDecimal;

public record PricingBreakdown(
        BigDecimal baseFare,
        BigDecimal peakSurcharge,
        BigDecimal alleySurcharge,
        BigDecimal floorSurcharge,
        BigDecimal porterFee,
        BigDecimal totalQuote
) {
}
