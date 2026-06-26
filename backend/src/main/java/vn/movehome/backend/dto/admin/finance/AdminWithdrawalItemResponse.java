package vn.movehome.backend.dto.admin.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminWithdrawalItemResponse(
        UUID id,
        UUID driverId,
        String driverName,
        String driverPhone,
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
