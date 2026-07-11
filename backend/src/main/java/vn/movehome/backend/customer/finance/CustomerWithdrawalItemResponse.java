package vn.movehome.backend.customer.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// 1 dong trong lich su yeu cau rut tien cua khach hang (GET /api/customer/wallet/withdrawals)
public record CustomerWithdrawalItemResponse(
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
