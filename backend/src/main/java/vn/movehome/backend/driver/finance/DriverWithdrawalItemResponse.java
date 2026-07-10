package vn.movehome.backend.driver.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// 1 dong trong lich su yeu cau rut tien cua tai xe (GET /api/driver/withdrawals)
public record DriverWithdrawalItemResponse(
        UUID id,
        BigDecimal amount,
        String status,
        String bankName,
        String bankAccountMasked,
        String rejectionReason,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt
) {
}
