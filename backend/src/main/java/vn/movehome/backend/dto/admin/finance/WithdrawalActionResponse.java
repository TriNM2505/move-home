package vn.movehome.backend.dto.admin.finance;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalActionResponse(
        UUID withdrawalId,
        String status,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String message
) {
}
