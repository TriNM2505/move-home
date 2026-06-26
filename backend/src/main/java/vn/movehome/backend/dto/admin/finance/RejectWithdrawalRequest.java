package vn.movehome.backend.dto.admin.finance;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RejectWithdrawalRequest(
        @JsonAlias({"reason", "rejection_reason", "rejectionReason"})
        String reason
) {
}
