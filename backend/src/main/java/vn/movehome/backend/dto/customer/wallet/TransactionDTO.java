package vn.movehome.backend.dto.customer.wallet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionDTO(
        UUID id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID relatedOrderId,
        String description,
        String vnpayTxnRefMasked,
        OffsetDateTime createdAt
) {
}
