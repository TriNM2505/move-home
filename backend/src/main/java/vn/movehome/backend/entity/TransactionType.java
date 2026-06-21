package vn.movehome.backend.entity;

/**
 * Loai giao dich tien trong he thong Move_home.
 * Constitution AC-13: moi giao dich phai duoc ghi vao bang transaction (audit trail).
 * AC-08: moi amount tuong ung phai la BigDecimal scale=0, VND nguyen dong.
 * POSITIVE amount = tien vao (cong); NEGATIVE amount = tien ra (tru).
 */
public enum TransactionType {

    /**
     * Driver dong coc 3.000.000 VND qua VNPay khi dang ky.
     * Coc la COLLATERAL cho DamageReport (CONTEXT.md §2).
     * Amount: +3_000_000 (cong vao deposit_balance cua Driver).
     */
    DEPOSIT_TOP_UP,

    /**
     * Hoan tra coc khi Driver nghi viec hoac Admin quyet dinh hoan tra.
     * Amount: -3_000_000 (tru khoi deposit_balance).
     */
    DEPOSIT_REFUND,

    /**
     * Customer thanh toan don hang (coc 30% hoac tra not 70%) qua VNPay.
     * Amount: + tong tien da nhan (cong vao tai khoan cong ty/trung gian).
     */
    ORDER_PAYMENT,

    /**
     * Customer nap tien vao vi noi bo qua VNPay.
     * Amount: positive, cong vao customer_wallet.balance va total_topped_up.
     */
    WALLET_TOP_UP,

    /**
     * Driver nhan phan thu nhap sau khi don COMPLETED va het escrow 2h.
     * Amount: +(total_quote * 70%) — 70% gia tri don hang sau khi tru commission.
     * Constitution AC-13: phai INSERT cung transaction voi UPDATE wallet.
     */
    DRIVER_EARNING,

    /**
     * Phi nen tang cong ty giu lai tu moi don COMPLETED.
     * Amount: +(total_quote * commission_rate_snapshot) — 30% gia tri don.
     * Ghi lai de audit; thuc te so tien nay nam trong tai khoan cong ty, khong phan phoi.
     */
    PLATFORM_FEE,

    /**
     * Tru tien boi thuong tu coc hoac vi Driver khi DamageReport duoc RESOLVED.
     * Thu tu tru: coc 3 trieu → vi Driver → SUSPEND neu het (CONTEXT.md §2 DamageReport).
     * Amount: negative (am), gia tri la so tien boi thuong thoa thuan.
     */
    DAMAGE_DEDUCTION,

    /**
     * Hoan tien cho Customer khi cong ty huy don (cancelled_by = COMPANY).
     * CONTEXT.md: hoan tien thu cong qua chuyen khoan, ghi RefundRecord.
     * Amount: negative am (tru khoi tai khoan trung gian cong ty).
     */
    REFUND
}
