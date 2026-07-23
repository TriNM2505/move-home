-- =============================================================================
-- Bang: transaction  (19/32)
-- Tac dung: SO CAI TIEN TE append-only cho CA he thong (vi tai xe + vi khach).
--           Ghi moi luong tien: coc/hoan coc, thanh toan don, nap vi, thu nhap
--           tai xe, phi nen tang 30%, khau tru DamageReport, hoan tien, rut tien.
--           KHONG UPDATE/DELETE — revert bang giao dich ADJUSTMENT moi.
--           vnpay_txn_ref UNIQUE dam bao idempotency IPN (HR-15).
-- Nguon migration: V6 (goc) + V13 (index) + V16 (related_dispute_id; type them roi bo
--                  DISPUTE_DEDUCTION) + V21 (type: +WALLET_TOP_UP, -DISPUTE_DEDUCTION)
--                  + V24 (related_withdrawal_id, balance_after, type +WITHDRAWAL)
--                  + V39 (related_customer_withdrawal_id).
-- Constitution: AC-13 (audit trail bat buoc), AC-08 (NUMERIC(15,0)), HR-15 (idempotency IPN).
-- Phu thuoc: app_user, service_order, dispute, withdrawal_request, customer_withdrawal_request.
-- =============================================================================

CREATE TABLE transaction (
    id                            UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id                       UUID          NOT NULL REFERENCES app_user(id),

    -- 9 loai giao dich cuoi cung (V24). Luu y: DISPUTE_DEDUCTION (them o V16) da bi BO o V21.
    type                          VARCHAR(30)   NOT NULL
                                  CHECK (type IN (
                                      'DEPOSIT_TOP_UP',    -- Driver dong coc 3 trieu
                                      'DEPOSIT_REFUND',    -- hoan coc khi Driver nghi
                                      'ORDER_PAYMENT',     -- Customer thanh toan don
                                      'WALLET_TOP_UP',     -- nap vi (V21)
                                      'DRIVER_EARNING',    -- Driver nhan 70% sau escrow
                                      'PLATFORM_FEE',      -- 30% phi nen tang
                                      'DAMAGE_DEDUCTION',  -- tru boi thuong DamageReport
                                      'REFUND',            -- hoan tien khach
                                      'WITHDRAWAL'         -- rut tien (V24)
                                  )),

    amount                        NUMERIC(15,0) NOT NULL,      -- POSITIVE=vao, NEGATIVE=ra (AC-08)
    related_order_id              UUID          REFERENCES service_order(id),
    description                   VARCHAR(255),
    vnpay_txn_ref                 VARCHAR(100)  UNIQUE,        -- HR-15: chong double IPN

    related_dispute_id            UUID          REFERENCES dispute(id),                       -- V16
    related_withdrawal_id         UUID          REFERENCES withdrawal_request(id),            -- V24
    balance_after                 NUMERIC(15,0),                                              -- V24: snapshot so du
    related_customer_withdrawal_id UUID         REFERENCES customer_withdrawal_request(id),   -- V39

    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),   -- append-only, khong updated_at

    CONSTRAINT pk_transaction PRIMARY KEY (id)
);

-- Truy van co ban
CREATE INDEX idx_transaction_user         ON transaction (user_id, created_at DESC);
CREATE INDEX idx_transaction_order        ON transaction (related_order_id) WHERE related_order_id IS NOT NULL;
CREATE INDEX idx_transaction_vnpay        ON transaction (vnpay_txn_ref) WHERE vnpay_txn_ref IS NOT NULL;
CREATE INDEX idx_transaction_created_id   ON transaction (created_at DESC, id DESC);
CREATE INDEX idx_transaction_type_created ON transaction (type, created_at DESC, id DESC);
CREATE INDEX idx_transaction_user_created ON transaction (user_id, created_at DESC, id DESC);
-- V13: moi don chi 1 DRIVER_EARNING
CREATE UNIQUE INDEX uq_transaction_driver_earning_order ON transaction (related_order_id)
    WHERE type = 'DRIVER_EARNING' AND related_order_id IS NOT NULL;
CREATE INDEX idx_transaction_driver_earning_created ON transaction (user_id, created_at DESC, id DESC)
    WHERE type = 'DRIVER_EARNING';
-- V16: lien ket dispute (refund khach / khau tru tai xe duy nhat theo dispute)
CREATE INDEX idx_transaction_dispute ON transaction (related_dispute_id, created_at ASC, id ASC)
    WHERE related_dispute_id IS NOT NULL;
CREATE UNIQUE INDEX uq_dispute_customer_refund ON transaction (related_dispute_id, user_id, type)
    WHERE type = 'REFUND' AND related_dispute_id IS NOT NULL;
-- Luu y: index sau (V16) loc theo type='DISPUTE_DEDUCTION' — loai nay da bo o V21 nen thuc te khong khop dong nao.
CREATE UNIQUE INDEX uq_dispute_driver_deduction ON transaction (related_dispute_id, user_id, type)
    WHERE type = 'DISPUTE_DEDUCTION' AND related_dispute_id IS NOT NULL;
-- V24: lien ket rut tien tai xe
CREATE UNIQUE INDEX uq_transaction_withdrawal ON transaction (related_withdrawal_id)
    WHERE type = 'WITHDRAWAL' AND related_withdrawal_id IS NOT NULL;
CREATE INDEX idx_transaction_withdrawal ON transaction (related_withdrawal_id, created_at DESC, id DESC)
    WHERE related_withdrawal_id IS NOT NULL;
-- V39: lien ket rut tien khach hang
CREATE UNIQUE INDEX uq_transaction_customer_withdrawal ON transaction (related_customer_withdrawal_id)
    WHERE type = 'WITHDRAWAL' AND related_customer_withdrawal_id IS NOT NULL;
CREATE INDEX idx_transaction_customer_withdrawal ON transaction (related_customer_withdrawal_id, created_at DESC, id DESC)
    WHERE related_customer_withdrawal_id IS NOT NULL;

COMMENT ON TABLE  transaction               IS 'So cai tien te append-only (AC-13). KHONG UPDATE/DELETE; revert = ADJUSTMENT.';
COMMENT ON COLUMN transaction.amount        IS 'VND: POSITIVE=cong, NEGATIVE=tru. NUMERIC(15,0) — AC-08.';
COMMENT ON COLUMN transaction.vnpay_txn_ref IS 'Ma giao dich VNPay. UNIQUE chong double IPN (HR-15).';
COMMENT ON COLUMN transaction.balance_after IS 'Snapshot so du vi ngay sau giao dich co tac dong den vi (V24).';
