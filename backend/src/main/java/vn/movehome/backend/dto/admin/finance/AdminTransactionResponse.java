package vn.movehome.backend.dto.admin.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminTransactionResponse(
        UUID id,
        String type,
        String typeLabel,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID userId,
        String userName,
        String userRole,
        String userEmail,
        UUID relatedOrderId,
        String orderCode,
        UUID relatedWithdrawalId,
        UUID relatedDisputeId,
        String vnpayTxnRefMasked,
        String bankTxnRefMasked,
        String description,
        OffsetDateTime createdAt
) {
}
