package vn.movehome.backend.driver.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WithdrawalRequestResponse(
        UUID id,
        BigDecimal amount,
        String status,
        String message,
        OffsetDateTime requestedAt
) {
}
