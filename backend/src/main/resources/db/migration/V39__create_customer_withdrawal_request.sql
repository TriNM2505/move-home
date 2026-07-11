-- =============================================================================
-- V39: Yeu cau rut tien cua khach hang tu customer_wallet.
-- Khach hang co the rut so du trong vi (vd tien hoan tu don cong ty huy) ve
-- tai khoan ngan hang. Admin duyet thu cong (giong luong rut tien tai xe).
-- Request PENDING chi giu cho theo logic nghiep vu; vi chi bi tru khi PROCESSED.
--
-- Tai su dung bang giao dich transaction (V6/V24) — KHONG tao bang giao dich moi.
-- AC-08: tien dung NUMERIC(15,0). AC-07: thoi gian dung TIMESTAMPTZ.
-- AC-14: trang thai dung VARCHAR + CHECK, khong dung PostgreSQL ENUM.
-- HR-18: customer_wallet.balance da co CHECK (balance >= 0); service validate truoc khi tru.
-- =============================================================================

-- 1) Bang yeu cau rut tien cua khach hang (mirror withdrawal_request cua tai xe).
CREATE TABLE customer_withdrawal_request (

    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    customer_id           UUID            NOT NULL
                          REFERENCES app_user(id),

    -- Khong gioi han toi thieu theo quyet dinh leader: chi can > 0 (VND nguyen dong).
    amount                NUMERIC(15,0)   NOT NULL
                          CHECK (amount > 0),

    -- Snapshot thong tin nhan tien tai thoi diem gui yeu cau.
    bank_code             VARCHAR(20)     NOT NULL,
    bank_name_snapshot    VARCHAR(100)    NOT NULL,
    bank_account_number   VARCHAR(20)     NOT NULL,
    bank_account_holder   VARCHAR(100)    NOT NULL,
    note                  VARCHAR(500),

    status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN (
                              'PENDING',
                              'PROCESSED',
                              'REJECTED',
                              'CANCELLED'
                          )),

    rejection_reason      VARCHAR(500),
    processed_by          UUID
                          REFERENCES app_user(id),
    bank_txn_ref          VARCHAR(100),

    requested_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,

    idempotency_key       UUID            NOT NULL,
    version               BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_customer_withdrawal_request PRIMARY KEY (id),
    CONSTRAINT uq_customer_withdrawal_idempotency UNIQUE (customer_id, idempotency_key)
);

-- Lich su rut tien cua 1 khach hang (moi nhat truoc).
CREATE INDEX idx_customer_withdrawal_customer_requested
    ON customer_withdrawal_request (customer_id, requested_at DESC, id DESC);

-- Hang doi PENDING cho Admin duyet theo FIFO.
CREATE INDEX idx_customer_withdrawal_pending_fifo
    ON customer_withdrawal_request (requested_at ASC, id ASC)
    WHERE status = 'PENDING';

-- Lich su da xu ly.
CREATE INDEX idx_customer_withdrawal_history_processed
    ON customer_withdrawal_request (processed_at DESC, id DESC)
    WHERE status IN ('PROCESSED', 'REJECTED', 'CANCELLED');

-- Ma giao dich ngan hang khong duoc trung (khi da co).
CREATE UNIQUE INDEX uq_customer_withdrawal_bank_txn_ref
    ON customer_withdrawal_request (bank_txn_ref)
    WHERE bank_txn_ref IS NOT NULL;

-- Rang buoc toan ven cac field theo trang thai (giong ck_withdrawal_terminal_fields cua tai xe).
-- NOT VALID: khong kiem tra du lieu cu (bang moi nen khong co du lieu cu), chi ap cho ban ghi moi.
ALTER TABLE customer_withdrawal_request
    ADD CONSTRAINT ck_customer_withdrawal_terminal_fields
    CHECK (
        (
            status = 'PENDING'
            AND processed_by IS NULL
            AND processed_at IS NULL
            AND bank_txn_ref IS NULL
            AND rejection_reason IS NULL
        )
        OR (
            status = 'PROCESSED'
            AND processed_by IS NOT NULL
            AND processed_at IS NOT NULL
            AND bank_txn_ref IS NOT NULL
            AND rejection_reason IS NULL
        )
        OR (
            status = 'REJECTED'
            AND processed_by IS NOT NULL
            AND processed_at IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND bank_txn_ref IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND bank_txn_ref IS NULL
        )
    ) NOT VALID;


-- 2) Them cot tong da rut vao vi khach hang (hien thi tren man hinh vi, doi soat).
ALTER TABLE customer_wallet
    ADD COLUMN IF NOT EXISTS total_withdrawn NUMERIC(15,0) NOT NULL DEFAULT 0;


-- 3) Lien ket giao dich WITHDRAWAL cua khach hang voi yeu cau rut tien.
--    Cot rieng, tach biet voi related_withdrawal_id (cua tai xe) de khong dung cham
--    unique index uq_transaction_withdrawal (chi ap khi related_withdrawal_id IS NOT NULL).
ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS related_customer_withdrawal_id UUID
        REFERENCES customer_withdrawal_request(id);

-- Chong double-process: moi yeu cau rut tien khach hang chi co toi da 1 giao dich WITHDRAWAL.
CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_customer_withdrawal
    ON transaction (related_customer_withdrawal_id)
    WHERE type = 'WITHDRAWAL'
      AND related_customer_withdrawal_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_customer_withdrawal
    ON transaction (related_customer_withdrawal_id, created_at DESC, id DESC)
    WHERE related_customer_withdrawal_id IS NOT NULL;


COMMENT ON TABLE customer_withdrawal_request IS 'Yeu cau rut tien cua khach hang tu customer_wallet; trang thai PENDING, PROCESSED, REJECTED hoac CANCELLED.';
COMMENT ON COLUMN customer_withdrawal_request.amount IS 'So tien rut nguyen VND; phai > 0.';
COMMENT ON COLUMN customer_withdrawal_request.idempotency_key IS 'Khoa chong tao trung yeu cau khi client retry.';
COMMENT ON COLUMN customer_wallet.total_withdrawn IS 'Tong tien khach hang da rut thanh cong khoi vi. NUMERIC(15,0) VND.';
COMMENT ON COLUMN transaction.related_customer_withdrawal_id IS 'Yeu cau rut tien khach hang lien quan den giao dich WITHDRAWAL.';
