-- =============================================================================
-- Bang: withdrawal_request  (13/32)
-- Tac dung: Yeu cau RUT TIEN cua Tai xe tu driver_wallet. Snapshot thong tin ngan
--           hang luc gui; Admin duyet thu cong (PROCESSED moi tru vi) hoac tu choi.
--           Co idempotency_key chong tao trung khi client retry.
-- Nguon migration: V12 (goc) + V24 (terminal-fields CHECK, index, unique bank_txn_ref).
-- Constitution: AC-08 (NUMERIC(15,0)), AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK).
-- =============================================================================

CREATE TABLE withdrawal_request (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    driver_id           UUID          NOT NULL REFERENCES app_user(id),
    amount              NUMERIC(15,0) NOT NULL CHECK (amount >= 100000),   -- toi thieu 100k

    -- Snapshot thong tin nhan tien
    bank_code           VARCHAR(20)   NOT NULL,
    bank_name_snapshot  VARCHAR(100)  NOT NULL,
    bank_account_number VARCHAR(20)   NOT NULL,
    bank_account_holder VARCHAR(100)  NOT NULL,
    note                VARCHAR(500),

    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSED', 'REJECTED', 'CANCELLED')),

    rejection_reason    VARCHAR(500),
    processed_by        UUID          REFERENCES app_user(id),
    bank_txn_ref        VARCHAR(100),

    requested_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,

    idempotency_key     UUID          NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_withdrawal_request     PRIMARY KEY (id),
    CONSTRAINT uq_withdrawal_idempotency UNIQUE (driver_id, idempotency_key),
    -- V24: toan ven field theo trang thai (NOT VALID vi ap cho ban ghi moi)
    CONSTRAINT ck_withdrawal_terminal_fields CHECK (
        (status = 'PENDING'   AND processed_by IS NULL     AND processed_at IS NULL
                              AND bank_txn_ref IS NULL      AND rejection_reason IS NULL)
     OR (status = 'PROCESSED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                              AND bank_txn_ref IS NOT NULL  AND rejection_reason IS NULL)
     OR (status = 'REJECTED'  AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                              AND rejection_reason IS NOT NULL AND bank_txn_ref IS NULL)
     OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND bank_txn_ref IS NULL)
    ) NOT VALID
);

CREATE INDEX idx_withdrawal_driver_requested       ON withdrawal_request (driver_id, requested_at DESC, id DESC);
CREATE INDEX idx_withdrawal_driver_status_requested ON withdrawal_request (driver_id, status, requested_at DESC);
CREATE INDEX idx_withdrawal_pending_fifo_v2        ON withdrawal_request (requested_at ASC, id ASC) WHERE status = 'PENDING';
CREATE INDEX idx_withdrawal_history_processed      ON withdrawal_request (processed_at DESC, id DESC) WHERE status IN ('PROCESSED', 'REJECTED', 'CANCELLED');
CREATE UNIQUE INDEX uq_withdrawal_bank_txn_ref     ON withdrawal_request (bank_txn_ref) WHERE bank_txn_ref IS NOT NULL;

COMMENT ON TABLE  withdrawal_request        IS 'Yeu cau rut tien tai xe. Vi chi bi tru khi PROCESSED (spec #007/#009).';
COMMENT ON COLUMN withdrawal_request.status IS 'PENDING | PROCESSED | REJECTED | CANCELLED (khong dung APPROVED/COMPLETED).';
