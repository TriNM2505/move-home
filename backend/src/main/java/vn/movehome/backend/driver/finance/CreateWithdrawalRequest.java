package vn.movehome.backend.driver.finance;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateWithdrawalRequest(
        @NotNull(message = "Vui lòng nhập số tiền cần rút.")
        @Digits(integer = 15, fraction = 0, message = "Số tiền rút phải là VND nguyên đồng.")
        BigDecimal amount
) {
}
