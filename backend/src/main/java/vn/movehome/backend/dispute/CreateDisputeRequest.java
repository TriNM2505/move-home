package vn.movehome.backend.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDisputeRequest(
        @NotBlank
        String claimType,

        @NotNull
        @Positive
        BigDecimal claimAmount,

        @NotBlank
        @Size(min = 10, max = 2000)
        String customerStatement
) {
}
