package vn.movehome.backend.dto.customer.wallet;

import java.math.BigDecimal;

public record WalletSummaryDTO(
        BigDecimal balance,
        BigDecimal totalToppedUp,
        BigDecimal totalSpent,
        BigDecimal totalWithdrawn
) {
}
