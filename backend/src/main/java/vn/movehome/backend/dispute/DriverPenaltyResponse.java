package vn.movehome.backend.dispute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Khoan phat dang cho tai xe nop bo sung (banner countdown tren trang driver)
public record DriverPenaltyResponse(
        UUID disputeId,
        String orderCode,
        BigDecimal shortfall,
        OffsetDateTime deadline
) {
}
