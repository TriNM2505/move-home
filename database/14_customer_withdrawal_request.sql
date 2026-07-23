-- =============================================================================
-- Bang: customer_withdrawal_request  (14/32)
-- Tac dung: Yeu cau RUT TIEN cua Khach hang tu customer_wallet (vd hoan tu don
--           cong ty huy). Cau truc mirror withdrawal_request cua tai xe; Admin
--           duyet thu cong. Khac biet: chi yeu cau amount > 0 (khong toi thieu 100k).
-- Nguon migration: V39.
-- Constitution: AC-08 (NUMERIC(15,0)), AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK), HR-18.
-- =============================================================================

CREATE TABLE customer_withdrawal_request (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    customer_id         UUID          NOT NULL REFERENCES app_user(id),
    amount              NUMERIC(15,0) NOT NULL CHECK (amount > 0),

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

    CONSTRAINT pk_customer_withdrawal_request     PRIMARY KEY (id),
    CONSTRAINT uq_customer_withdrawal_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_customer_withdrawal_terminal_fields CHECK (
        (status = 'PENDING'   AND processed_by IS NULL     AND processed_at IS NULL
                              AND bank_txn_ref IS NULL      AND rejection_reason IS NULL)
     OR (status = 'PROCESSED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                              AND bank_txn_ref IS NOT NULL  AND rejection_reason IS NULL)
     OR (status = 'REJECTED'  AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                              AND rejection_reason IS NOT NULL AND bank_txn_ref IS NULL)
     OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND bank_txn_ref IS NULL)
    ) NOT VALID
);

CREATE INDEX idx_customer_withdrawal_customer_requested ON customer_withdrawal_request (customer_id, requested_at DESC, id DESC);
CREATE INDEX idx_customer_withdrawal_pending_fifo       ON customer_withdrawal_request (requested_at ASC, id ASC) WHERE status = 'PENDING';
CREATE INDEX idx_customer_withdrawal_history_processed  ON customer_withdrawal_request (processed_at DESC, id DESC) WHERE status IN ('PROCESSED', 'REJECTED', 'CANCELLED');
CREATE UNIQUE INDEX uq_customer_withdrawal_bank_txn_ref ON customer_withdrawal_request (bank_txn_ref) WHERE bank_txn_ref IS NOT NULL;

COMMENT ON TABLE customer_withdrawal_request IS 'Yeu cau rut tien khach hang tu customer_wallet; Admin duyet thu cong (spec #021).';
