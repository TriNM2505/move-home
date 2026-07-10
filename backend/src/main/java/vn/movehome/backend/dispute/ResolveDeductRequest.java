package vn.movehome.backend.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Manager khau tru tai xe: tru vi ngay, thieu thi tai xe co 2 phut nop bo sung
public record ResolveDeductRequest(
        @NotNull
        @Positive
        BigDecimal deductAmount,

        @NotBlank
        @Size(min = 10, max = 1000)
        String note
) {
}
