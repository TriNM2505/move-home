package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Nguon tinh so tien COC duy nhat cho ca 2 hinh thuc tra coc (VNPay + Vi),
 * de hai luong luon ra cung mot con so (tranh sai lech tien - AC-08).
 */
public final class OrderDepositCalculator {

    private OrderDepositCalculator() {
    }

    /**
     * Coc = total_quote × commission_rate_snapshot, lam tron XUONG (FLOOR) ve VND nguyen dong (AC-08).
     * Dung snapshot tren don de don cu giu dung ty le du Admin doi commission sau nay.
     */
    public static BigDecimal deposit(ServiceOrder order) {
        BigDecimal total = order.getTotalQuote();
        BigDecimal rate = order.getCommissionRateSnapshot();
        if (total == null || rate == null) {
            throw new IllegalStateException(
                    "Order thieu total_quote hoac commission_rate_snapshot de tinh coc");
        }
        return total.multiply(rate).setScale(0, RoundingMode.FLOOR);
    }

    /**
     * Phan con lai (70%) khach tra not = total_quote − deposit.
     * deposit + finalAmount = total_quote (khop tuyet doi, AC-08).
     */
    public static BigDecimal finalAmount(ServiceOrder order) {
        return order.getTotalQuote().subtract(deposit(order));
    }
}
