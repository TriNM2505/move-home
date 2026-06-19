package vn.movehome.backend.driver.finance;

import java.math.BigDecimal;

public record DriverWalletSummaryResponse(
        BigDecimal balance,
        BigDecimal totalEarned,
        BigDecimal totalWithdrawn
) {
}
