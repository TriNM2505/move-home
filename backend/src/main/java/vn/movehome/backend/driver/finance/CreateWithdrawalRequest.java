package vn.movehome.backend.driver.finance;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateWithdrawalRequest(
        @NotNull(message = "Vui lòng nhập số tiền cần rút.")
        @Digits(integer = 15, fraction = 0, message = "Số tiền rút phải là VND nguyên đồng.")
        BigDecimal amount,

        // Ma ngan hang nhan tien (vd VCB, BIDV) — service validate theo whitelist SUPPORTED_BANKS
        @JsonAlias({"bank_code", "bankCode"})
        String bankCode,

        // So tai khoan nhan tien — service validate 8-15 chu so
        @JsonAlias({"bank_account_number", "bankAccountNumber"})
        String bankAccountNumber
) {
}
