-- =============================================================================
-- V12: Tạo bảng yêu cầu rút tiền của tài xế theo Spec #007.
-- Request PENDING chỉ giữ chỗ theo logic nghiệp vụ; ví chỉ bị trừ khi PROCESSED.
-- AC-08: tiền dùng NUMERIC(15,0). AC-07: thời gian dùng TIMESTAMPTZ.
-- AC-14: trạng thái dùng VARCHAR + CHECK, không dùng PostgreSQL ENUM.
-- =============================================================================

CREATE TABLE withdrawal_request (

    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    driver_id             UUID            NOT NULL
                          REFERENCES app_user(id),

    amount                NUMERIC(15,0)   NOT NULL
                          CHECK (amount >= 100000),

    -- Snapshot thông tin nhận tiền tại thời điểm gửi yêu cầu.
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

    idempotency_key       UUID             NOT NULL,
    version               BIGINT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_withdrawal_request PRIMARY KEY (id),
    CONSTRAINT uq_withdrawal_idempotency UNIQUE (driver_id, idempotency_key)
);


CREATE INDEX idx_withdrawal_driver_requested
    ON withdrawal_request (driver_id, requested_at DESC, id DESC);

CREATE INDEX idx_withdrawal_pending_fifo
    ON withdrawal_request (requested_at ASC)
    WHERE status = 'PENDING';


COMMENT ON TABLE withdrawal_request IS 'Yêu cầu rút tiền của tài xế theo trạng thái PENDING, PROCESSED, REJECTED hoặc CANCELLED.';
COMMENT ON COLUMN withdrawal_request.amount IS 'Số tiền rút nguyên VND; tối thiểu 100.000 đồng.';
COMMENT ON COLUMN withdrawal_request.status IS 'Trạng thái canonical theo Spec #007; không dùng APPROVED hoặc COMPLETED.';
COMMENT ON COLUMN withdrawal_request.idempotency_key IS 'Khóa chống tạo trùng yêu cầu khi client retry.';
