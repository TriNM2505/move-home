package vn.movehome.backend.dto.admin.finance;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

public record UpdateCommissionSettingsRequest(
        Long version,

        @JsonAlias({"commission_rate", "commissionRate"})
        BigDecimal commissionRate
) {
}
