package vn.movehome.backend.customer.finance;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body tao yeu cau rut tien cua khach hang.
 * Khong gioi han so tien toi thieu (theo quyet dinh leader) — chi can > 0, service validate them.
 */
public record CreateCustomerWithdrawalRequest(
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
