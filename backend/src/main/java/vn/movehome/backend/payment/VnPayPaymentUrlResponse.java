package vn.movehome.backend.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VnPayPaymentUrlResponse(
        @JsonProperty("payment_url")
        String paymentUrl,
        @JsonProperty("txn_ref")
        String txnRef,
        BigDecimal amount,
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt
) {
}
