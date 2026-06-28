package vn.movehome.backend.dto.admin.list;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WithdrawalListItem(
        @JsonProperty("id") UUID id,
        @JsonProperty("driver_id") UUID driverId,
        @JsonProperty("driver_name") String driverName,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("bank_name") String bankName,
        @JsonProperty("bank_account_masked") String bankAccountMasked,
        @JsonProperty("status") String status,
        @JsonProperty("requested_at") OffsetDateTime requestedAt,
        @JsonProperty("processed_at") OffsetDateTime processedAt,
        @JsonProperty("processor_name") String processorName,
        @JsonProperty("bank_txn_ref_masked") String bankTxnRefMasked
) {
}
