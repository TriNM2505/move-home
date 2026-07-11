package vn.movehome.backend.dto.admin.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// 1 dong trong hang doi Admin duyet rut tien khach hang (GET /api/admin/customer-withdrawals/pending)
public record AdminCustomerWithdrawalItemResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String customerPhone,
        BigDecimal amount,
        String bankCode,
        String bankName,
        String bankAccountMasked,
        OffsetDateTime requestedAt,
        long daysWaiting,
        BigDecimal walletBalance,
        boolean processReady,
        List<String> blockingReasons
) {
}
