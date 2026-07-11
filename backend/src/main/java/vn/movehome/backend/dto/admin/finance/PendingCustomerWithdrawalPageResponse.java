package vn.movehome.backend.dto.admin.finance;

import java.math.BigDecimal;
import java.util.List;

public record PendingCustomerWithdrawalPageResponse(
        List<AdminCustomerWithdrawalItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        long pendingCount,
        BigDecimal pendingAmount,
        long oldestWaitingDays,
        long overSlaCount
) {
}
