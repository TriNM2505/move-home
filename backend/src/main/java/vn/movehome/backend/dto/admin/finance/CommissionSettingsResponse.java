package vn.movehome.backend.dto.admin.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommissionSettingsResponse(
        Long version,
        BigDecimal commissionRate,
        OffsetDateTime lastUpdatedAt,
        UUID lastUpdatedBy
) {
}
