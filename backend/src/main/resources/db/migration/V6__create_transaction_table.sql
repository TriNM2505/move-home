-- =============================================================================
-- V6: Tao bang transaction — Audit trail moi luong tien trong he thong
-- Constitution AC-13: moi giao dich tien PHAI duoc ghi vao bang nay.
-- Append-only: KHONG UPDATE, KHONG DELETE. Revert = them ADJUSTMENT transaction moi.
-- AC-08: amount dung NUMERIC(15,0) — VND nguyen dong.
-- AC-07: created_at dung TIMESTAMPTZ.
-- =============================================================================

CREATE TABLE transaction (

    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Chu the cua giao dich (Driver, Customer, hoac System user)
    user_id             UUID            NOT NULL
                        REFERENCES app_user(id),

    -- Loai giao dich — xem comment cua TransactionType enum trong Java
    type                VARCHAR(30)     NOT NULL
                        CHECK (type IN (
                            'DEPOSIT_TOP_UP',   -- Driver dong coc 3 trieu
                            'DEPOSIT_REFUND',   -- Hoan coc khi Driver nghi
                            'ORDER_PAYMENT',    -- Customer thanh toan don
                            'DRIVER_EARNING',   -- Driver nhan 70% sau escrow
                            'PLATFORM_FEE',     -- 30% phi nen tang cong ty giu
                            'DAMAGE_DEDUCTION', -- Tru boi thuong DamageReport
                            'REFUND'            -- Hoan tien cho Customer
                        )),

    -- So tien VND: POSITIVE = cong tien vao, NEGATIVE = tru tien ra (AC-08)
    -- NUMERIC(15,0): du lon cho tong don hang lon nhat (~999 ty VND, khong the xay ra)
    amount              NUMERIC(15,0)   NOT NULL,

    -- Don hang lien quan — NULL cho DEPOSIT_TOP_UP, DEPOSIT_REFUND (khong lien quan don)
    related_order_id    UUID
                        REFERENCES service_order(id),

    -- Mo ta ngan hien thi trong lich su vi cua Driver hoac bao cao Admin
    description         VARCHAR(255),

    -- Ma giao dich VNPay — NULL cho giao dich noi bo, co gia tri cho IPN callbacks
    -- UNIQUE: chong double-processing IPN (Constitution HR-15)
    vnpay_txn_ref       VARCHAR(100)    UNIQUE,

    -- Append-only: chi co created_at, khong co updated_at
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transaction PRIMARY KEY (id)
);


-- Index chinh cho lich su vi Driver: GET /api/driver/wallet/history (sort moi truoc)
CREATE INDEX idx_transaction_user
    ON transaction (user_id, created_at DESC);

-- Index tim giao dich theo don hang (khi huy don → kiem tra cac giao dich lien quan)
CREATE INDEX idx_transaction_order
    ON transaction (related_order_id)
    WHERE related_order_id IS NOT NULL;

-- Index kiem tra VNPay idempotency (HR-15 — kiem tra nhanh truoc khi xu ly IPN)
CREATE INDEX idx_transaction_vnpay
    ON transaction (vnpay_txn_ref)
    WHERE vnpay_txn_ref IS NOT NULL;


COMMENT ON TABLE  transaction              IS 'Audit trail tien te: append-only. KHONG UPDATE/DELETE. Revert = them ADJUSTMENT. Constitution AC-13.';
COMMENT ON COLUMN transaction.amount       IS 'VND nguyen dong: POSITIVE=cong, NEGATIVE=tru. NUMERIC(15,0) — AC-08.';
COMMENT ON COLUMN transaction.vnpay_txn_ref IS 'Ma giao dich VNPay. UNIQUE chong double IPN (HR-15). NULL neu khong qua VNPay.';
