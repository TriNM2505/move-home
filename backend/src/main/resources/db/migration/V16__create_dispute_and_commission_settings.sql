-- =============================================================================
-- V16: Tạo schema khiếu nại và cấu hình chính sách giá/hoa hồng Sprint 5.
-- Spec 010: trạng thái đơn canonical là IN_DISPUTE; DISPUTED chỉ là alias legacy.
-- Spec 014: cấu hình dùng một bản ghi active có version và lịch sử bất biến.
-- AC-07: mọi timestamp dùng TIMESTAMPTZ.
-- AC-08: tiền dùng NUMERIC(15,0), tỷ lệ dùng NUMERIC(5,4).
-- AC-14: trạng thái dùng VARCHAR + CHECK, không dùng PostgreSQL ENUM.
-- HR-21: tên bảng tránh PostgreSQL reserved words.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Chuẩn hóa trạng thái tranh chấp của service_order theo Spec 010.
-- V5 tạo constraint inline nên PostgreSQL đặt tên service_order_status_check.
-- Giữ các trạng thái demo hiện có và thay DISPUTED bằng IN_DISPUTE.
-- -----------------------------------------------------------------------------
ALTER TABLE service_order
    DROP CONSTRAINT IF EXISTS service_order_status_check;

UPDATE service_order
SET status = 'IN_DISPUTE'
WHERE status = 'DISPUTED';

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_status
        CHECK (status IN (
            'PENDING',
            'ACCEPTED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED',
            'IN_DISPUTE'
        ));


-- -----------------------------------------------------------------------------
-- Khiếu nại canonical theo Spec 010.
-- Một order chỉ có tối đa một dispute chưa giải quyết tại một thời điểm.
-- -----------------------------------------------------------------------------
CREATE TABLE dispute (
    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    order_id              UUID            NOT NULL
                          REFERENCES service_order(id),
    customer_id           UUID            NOT NULL
                          REFERENCES app_user(id),
    driver_id             UUID            NOT NULL
                          REFERENCES app_user(id),

    claim_type            VARCHAR(30)     NOT NULL
                          CHECK (claim_type IN (
                              'DAMAGE',
                              'MISSING_ITEM',
                              'LATE_DELIVERY',
                              'INAPPROPRIATE_BEHAVIOR',
                              'OTHER'
                          )),
    claim_amount          NUMERIC(15,0)   NOT NULL
                          CHECK (claim_amount > 0),
    customer_statement    TEXT            NOT NULL,
    driver_response       TEXT,
    driver_response_at    TIMESTAMPTZ,

    status                VARCHAR(30)     NOT NULL DEFAULT 'OPEN'
                          CHECK (status IN (
                              'OPEN',
                              'INVESTIGATING',
                              'RESOLVED_REFUND',
                              'RESOLVED_DEDUCT',
                              'CLOSED_NO_FAULT'
                          )),
    resolution_amount     NUMERIC(15,0),
    resolution_note       TEXT,
    resolved_by           UUID
                          REFERENCES app_user(id),
    resolved_at           TIMESTAMPTZ,
    deadline              TIMESTAMPTZ     NOT NULL,
    version               BIGINT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute PRIMARY KEY (id),
    CONSTRAINT uq_dispute_order_open UNIQUE NULLS NOT DISTINCT
        (order_id, resolved_at),
    CONSTRAINT ck_dispute_resolution_fields CHECK (
        (
            status IN ('OPEN', 'INVESTIGATING')
            AND resolution_amount IS NULL
            AND resolution_note IS NULL
            AND resolved_by IS NULL
            AND resolved_at IS NULL
        )
        OR
        (
            status IN ('RESOLVED_REFUND', 'RESOLVED_DEDUCT')
            AND resolution_amount IS NOT NULL
            AND resolution_amount > 0
            AND resolution_note IS NOT NULL
            AND resolved_by IS NOT NULL
            AND resolved_at IS NOT NULL
        )
        OR
        (
            status = 'CLOSED_NO_FAULT'
            AND resolution_amount IS NULL
            AND resolution_note IS NOT NULL
            AND resolved_by IS NOT NULL
            AND resolved_at IS NOT NULL
        )
    )
);

CREATE INDEX idx_dispute_pending
    ON dispute (created_at ASC, id ASC)
    WHERE status IN ('OPEN', 'INVESTIGATING');

CREATE INDEX idx_dispute_history
    ON dispute (created_at DESC, id DESC);

CREATE INDEX idx_dispute_customer_history
    ON dispute (customer_id, created_at DESC);

CREATE INDEX idx_dispute_driver_history
    ON dispute (driver_id, created_at DESC);

CREATE TRIGGER trg_dispute_updated_at
    BEFORE UPDATE ON dispute
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- -----------------------------------------------------------------------------
-- Bằng chứng và bình luận nội bộ của khiếu nại theo Spec 010.
-- -----------------------------------------------------------------------------
CREATE TABLE dispute_evidence (
    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    dispute_id            UUID            NOT NULL
                          REFERENCES dispute(id),
    uploader_id           UUID            NOT NULL
                          REFERENCES app_user(id),
    uploader_role         VARCHAR(20)     NOT NULL
                          CHECK (uploader_role IN (
                              'CUSTOMER',
                              'DRIVER',
                              'MANAGER',
                              'ADMIN'
                          )),
    evidence_type         VARCHAR(30)     NOT NULL
                          CHECK (evidence_type IN ('PHOTO', 'DOCUMENT', 'OTHER')),
    cloudinary_public_id  TEXT            NOT NULL,
    content_type          VARCHAR(100)    NOT NULL,
    file_size_bytes       BIGINT          NOT NULL
                          CHECK (file_size_bytes > 0),
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_evidence PRIMARY KEY (id)
);

CREATE INDEX idx_dispute_evidence_timeline
    ON dispute_evidence (dispute_id, created_at ASC, id ASC);


CREATE TABLE dispute_comment (
    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    dispute_id            UUID            NOT NULL
                          REFERENCES dispute(id),
    author_id             UUID            NOT NULL
                          REFERENCES app_user(id),
    author_role           VARCHAR(20)     NOT NULL
                          CHECK (author_role IN ('MANAGER', 'ADMIN')),
    comment               TEXT            NOT NULL,
    idempotency_key       UUID            NOT NULL,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_comment PRIMARY KEY (id),
    CONSTRAINT uq_dispute_comment_idempotency
        UNIQUE (author_id, idempotency_key)
);

CREATE INDEX idx_dispute_comment_timeline
    ON dispute_comment (dispute_id, created_at ASC, id ASC);


-- -----------------------------------------------------------------------------
-- Liên kết sổ cái thật của dự án (bảng transaction từ V6) với dispute.
-- Bổ sung loại DISPUTE_DEDUCTION, vẫn giữ toàn bộ loại giao dịch hiện có.
-- -----------------------------------------------------------------------------
ALTER TABLE transaction
    ADD COLUMN related_dispute_id UUID
        REFERENCES dispute(id);

ALTER TABLE transaction
    DROP CONSTRAINT IF EXISTS transaction_type_check;

ALTER TABLE transaction
    ADD CONSTRAINT ck_transaction_type CHECK (type IN (
        'DEPOSIT_TOP_UP',
        'DEPOSIT_REFUND',
        'ORDER_PAYMENT',
        'DRIVER_EARNING',
        'PLATFORM_FEE',
        'DAMAGE_DEDUCTION',
        'DISPUTE_DEDUCTION',
        'REFUND'
    ));

CREATE INDEX idx_transaction_dispute
    ON transaction (related_dispute_id, created_at ASC, id ASC)
    WHERE related_dispute_id IS NOT NULL;

CREATE UNIQUE INDEX uq_dispute_customer_refund
    ON transaction (related_dispute_id, user_id, type)
    WHERE type = 'REFUND'
      AND related_dispute_id IS NOT NULL;

CREATE UNIQUE INDEX uq_dispute_driver_deduction
    ON transaction (related_dispute_id, user_id, type)
    WHERE type = 'DISPUTE_DEDUCTION'
      AND related_dispute_id IS NOT NULL;


-- -----------------------------------------------------------------------------
-- Cấu hình pricing/hoa hồng active singleton theo Spec 014.
-- Mỗi lần cập nhật tăng version; order cũ tiếp tục dùng snapshot đã lưu.
-- -----------------------------------------------------------------------------
CREATE TABLE commission_settings (
    id                      INTEGER         NOT NULL DEFAULT 1
                            CHECK (id = 1),
    commission_rate         NUMERIC(5,4)    NOT NULL DEFAULT 0.3000
                            CHECK (commission_rate BETWEEN 0.0500 AND 0.5000),
    peak_surcharge_rate     NUMERIC(5,4)    NOT NULL DEFAULT 0.3000
                            CHECK (peak_surcharge_rate BETWEEN 0.0000 AND 1.0000),
    peak_hours              JSONB           NOT NULL DEFAULT
                            '[{"start":"07:00","end":"09:00"},{"start":"17:00","end":"19:00"}]',
    alley_surcharge_rate    NUMERIC(5,4)    NOT NULL DEFAULT 0.2000
                            CHECK (alley_surcharge_rate BETWEEN 0.0000 AND 1.0000),
    floor_surcharge_tiers   JSONB           NOT NULL DEFAULT
                            '[{"min_floor":2,"max_floor":3,"rate":0.2000},{"min_floor":4,"max_floor":5,"rate":0.3000},{"min_floor":6,"max_floor":30,"rate":0.5000}]',
    base_rate_per_km        JSONB           NOT NULL DEFAULT
                            '{"TRUCK_500KG":20000,"TRUCK_1T":30000,"TRUCK_15T":40000}',
    porter_fee_per_person   JSONB           NOT NULL DEFAULT
                            '{"TRUCK_500KG":150000,"TRUCK_1T":200000,"TRUCK_15T":300000}',
    driver_deposit_vnd      NUMERIC(15,0)   NOT NULL DEFAULT 3000000
                            CHECK (driver_deposit_vnd BETWEEN 0 AND 50000000),
    min_withdrawal_vnd      NUMERIC(15,0)   NOT NULL DEFAULT 100000
                            CHECK (min_withdrawal_vnd BETWEEN 50000 AND 1000000),
    version                 BIGINT          NOT NULL DEFAULT 1
                            CHECK (version > 0),
    last_updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_updated_by         UUID
                            REFERENCES app_user(id),

    CONSTRAINT pk_commission_settings PRIMARY KEY (id)
);

INSERT INTO commission_settings (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;


-- Lịch sử là snapshot chỉ-thêm; service không được UPDATE/DELETE bản ghi này.
CREATE TABLE commission_settings_history (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    from_version    BIGINT          NOT NULL,
    to_version      BIGINT          NOT NULL,
    changed_by      UUID            NOT NULL
                    REFERENCES app_user(id),
    old_values      JSONB           NOT NULL,
    new_values      JSONB           NOT NULL,
    diff            JSONB           NOT NULL,
    note            VARCHAR(1000),
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_commission_settings_history PRIMARY KEY (id),
    CONSTRAINT uq_settings_history_to_version UNIQUE (to_version),
    CONSTRAINT ck_settings_history_version_progression
        CHECK (to_version > from_version)
);

CREATE INDEX idx_settings_history_changed_at
    ON commission_settings_history (changed_at DESC, id DESC);


COMMENT ON TABLE dispute IS 'Khiếu nại đơn hàng theo Spec 010; quyết định terminal là bất biến.';
COMMENT ON COLUMN dispute.status IS 'OPEN, INVESTIGATING, RESOLVED_REFUND, RESOLVED_DEDUCT hoặc CLOSED_NO_FAULT.';
COMMENT ON COLUMN dispute.claim_amount IS 'Số tiền Customer yêu cầu, VND nguyên đồng NUMERIC(15,0).';
COMMENT ON COLUMN transaction.related_dispute_id IS 'Khiếu nại liên quan đến giao dịch hoàn tiền hoặc khấu trừ.';
COMMENT ON TABLE commission_settings IS 'Một bản ghi cấu hình pricing/hoa hồng active có optimistic version theo Spec 014.';
COMMENT ON COLUMN commission_settings.commission_rate IS 'Tỷ lệ hoa hồng áp dụng cho quote/order mới; order cũ giữ commission_rate_snapshot.';
COMMENT ON TABLE commission_settings_history IS 'Lịch sử snapshot cấu hình chỉ-thêm, phục vụ audit và đối soát.';
