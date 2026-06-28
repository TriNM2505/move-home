package vn.movehome.backend.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ResolveDisputeRequest(
        @NotNull
        @Positive
        BigDecimal refundAmount,

        @NotBlank
        @Size(min = 10, max = 1000)
        String note
) {
}
