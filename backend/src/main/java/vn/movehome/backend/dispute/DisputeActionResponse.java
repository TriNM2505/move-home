package vn.movehome.backend.dispute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DisputeActionResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        String orderStatus,
        String status,
        BigDecimal resolutionAmount,
        String resolutionNote,
        UUID resolvedBy,
        OffsetDateTime resolvedAt,
        String message
) {
}
