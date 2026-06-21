package vn.movehome.backend.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateWalletTopUpRequest(
        @NotNull
        @Positive
        BigDecimal amount
) {
}
