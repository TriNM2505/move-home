package vn.movehome.backend.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnPayReturnResponse(
        @JsonProperty("signature_valid")
        boolean signatureValid,
        boolean successful,
        @JsonProperty("txn_ref")
        String txnRef,
        @JsonProperty("response_code")
        String responseCode,
        String message,
        @JsonProperty("processing_status")
        String processingStatus
) {
}
