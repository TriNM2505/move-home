package vn.movehome.backend.customer.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Ket qua tao yeu cau rut tien cua khach hang (POST /api/customer/wallet/withdrawals)
public record CustomerWithdrawalRequestResponse(
        UUID id,
        BigDecimal amount,
        String status,
        String message,
        OffsetDateTime requestedAt
) {
}
