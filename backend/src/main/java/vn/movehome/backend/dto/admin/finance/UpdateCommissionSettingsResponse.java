package vn.movehome.backend.dto.admin.finance;

import java.time.OffsetDateTime;

public record UpdateCommissionSettingsResponse(
        String message,
        CommissionSettingsResponse newSettings,
        OffsetDateTime effectiveFrom,
        Long version
) {
}
