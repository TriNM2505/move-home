package vn.movehome.backend.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho OrderDepositCalculator — nguon tinh coc duy nhat (AC-08),
 * lam tron XUONG (FLOOR) ve VND nguyen dong.
 */
class OrderDepositCalculatorTest {

    private ServiceOrder orderWith(BigDecimal total, BigDecimal rate) {
        return ServiceOrder.builder()
                .totalQuote(total)
                .commissionRateSnapshot(rate)
                .build();
    }

    @Test
    void deposit_totalQuoteNull_throwsIllegalState() {
        ServiceOrder order = orderWith(null, new BigDecimal("0.3000"));

        assertThatThrownBy(() -> OrderDepositCalculator.deposit(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order thieu total_quote hoac commission_rate_snapshot de tinh coc");
    }

    @Test
    void deposit_commissionRateNull_throwsIllegalState() {
        ServiceOrder order = orderWith(new BigDecimal("1000000"), null);

        assertThatThrownBy(() -> OrderDepositCalculator.deposit(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order thieu total_quote hoac commission_rate_snapshot de tinh coc");
    }

    @Test
    void deposit_roundsDownToFloor() {
        // 1000001 * 0.3000 = 300000.3 -> FLOOR -> 300000 (khong phai HALF_UP 300000 lan nay trung,
        // dung so le hon de phan biet ro rang FLOOR vs HALF_UP)
        ServiceOrder order = orderWith(new BigDecimal("1000009"), new BigDecimal("0.3000"));

        BigDecimal deposit = OrderDepositCalculator.deposit(order);

        // 1000009 * 0.3 = 300002.7 -> FLOOR -> 300002 (HALF_UP se ra 300003)
        assertThat(deposit).isEqualByComparingTo(new BigDecimal("300002"));
    }

    @Test
    void finalAmount_equalsTotalMinusDeposit() {
        ServiceOrder order = orderWith(new BigDecimal("1000009"), new BigDecimal("0.3000"));

        BigDecimal deposit = OrderDepositCalculator.deposit(order);
        BigDecimal finalAmount = OrderDepositCalculator.finalAmount(order);

        assertThat(finalAmount).isEqualByComparingTo(order.getTotalQuote().subtract(deposit));
        assertThat(deposit.add(finalAmount)).isEqualByComparingTo(order.getTotalQuote());
    }
}
