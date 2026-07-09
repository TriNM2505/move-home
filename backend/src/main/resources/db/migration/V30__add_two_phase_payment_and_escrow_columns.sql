-- =============================================================================
-- V30: Ho tro luong thanh toan 2 giai doan (coc 30% + tra not 70%) va escrow 2h.
-- AC-07: timestamp dung TIMESTAMPTZ (luu UTC). AC-08: tien van NUMERIC(15,0) (khong doi o day).
-- =============================================================================

-- Noi cot status: 'AWAITING_FINAL_PAYMENT' dai 22 ky tu, vuot VARCHAR(20) cu.
-- CHECK constraint ck_service_order_status (V21) da liet ke gia tri nay nen khong doi.
ALTER TABLE service_order
    ALTER COLUMN status TYPE VARCHAR(30);

-- Thoi diem khach tra not 70% (xac nhan boi VNPay IPN). NULL = chua tra not.
ALTER TABLE service_order
    ADD COLUMN IF NOT EXISTS final_paid_at TIMESTAMPTZ;
COMMENT ON COLUMN service_order.final_paid_at
    IS 'Thoi diem khach tra not 70% (xac nhan boi VNPay IPN); NULL = chua tra.';

-- Thoi diem escrow release 70% vao vi tai xe. NULL = chua release (dang trong escrow 2h).
ALTER TABLE service_order
    ADD COLUMN IF NOT EXISTS earning_released_at TIMESTAMPTZ;
COMMENT ON COLUMN service_order.earning_released_at
    IS 'Thoi diem escrow release 70% vao vi tai xe; NULL = chua release.';

-- Index phuc vu scheduled job escrow: quet don COMPLETED chua release, da qua 2h.
CREATE INDEX IF NOT EXISTS idx_order_escrow_pending
    ON service_order (completed_at)
    WHERE status = 'COMPLETED' AND earning_released_at IS NULL AND deleted_at IS NULL;
