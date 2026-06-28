package vn.movehome.backend.dto.admin.list;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** DTO raw từ query. Service map sang WithdrawalListItem (masked) trước khi trả response. */
public record WithdrawalListItemRaw(
        UUID id,
        UUID driverId,
        String driverName,
        BigDecimal amount,
        String bankName,
        String bankAccountNumber,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt,
        String processorName,
        String bankTxnRef
) {
}
