-- =============================================================================
-- Bang: dispute  (15/32)
-- Tac dung: Khieu nai/tranh chap gan voi 1 don (DamageReport, giao tre, sai tai
--           xe...). Manager dieu tra roi ket luan terminal: hoan tien khach
--           (RESOLVED_REFUND), khau tru tai xe (RESOLVED_DEDUCT) hoac khong loi
--           (CLOSED_NO_FAULT). Quyet dinh terminal la bat bien (CHECK toan ven).
-- Nguon migration: V16 (goc) + V34 (pending_deduct_shortfall + deadline)
--                  + V37 (claim_type them DRIVER_MISMATCH).
-- Constitution: AC-08 (NUMERIC(15,0)), AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK), HR-06/07.
-- Phu thuoc: service_order, app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE dispute (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id                 UUID          NOT NULL REFERENCES service_order(id),
    customer_id              UUID          NOT NULL REFERENCES app_user(id),
    driver_id                UUID          NOT NULL REFERENCES app_user(id),

    claim_type               VARCHAR(30)   NOT NULL,     -- CHECK ck_dispute_claim_type ben duoi (V37)
    claim_amount             NUMERIC(15,0) NOT NULL CHECK (claim_amount > 0),
    customer_statement       TEXT          NOT NULL,
    driver_response          TEXT,
    driver_response_at       TIMESTAMPTZ,

    status                   VARCHAR(30)   NOT NULL DEFAULT 'OPEN'
                             CHECK (status IN ('OPEN', 'INVESTIGATING',
                                               'RESOLVED_REFUND', 'RESOLVED_DEDUCT', 'CLOSED_NO_FAULT')),
    resolution_amount        NUMERIC(15,0),
    resolution_note          TEXT,
    resolved_by              UUID          REFERENCES app_user(id),
    resolved_at              TIMESTAMPTZ,
    deadline                 TIMESTAMPTZ   NOT NULL,

    -- V34: khau tru vi khong du -> ghi phan thieu + han nop bo sung
    pending_deduct_shortfall NUMERIC(15,0),
    deduct_deadline          TIMESTAMPTZ,

    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute PRIMARY KEY (id),
    -- 1 don chi 1 dispute chua giai quyet tai 1 thoi diem (NULLS NOT DISTINCT: resolved_at NULL = dang mo)
    CONSTRAINT uq_dispute_order_open UNIQUE NULLS NOT DISTINCT (order_id, resolved_at),
    -- V37: mo rong claim_type
    CONSTRAINT ck_dispute_claim_type CHECK (claim_type IN (
        'DAMAGE', 'MISSING_ITEM', 'LATE_DELIVERY', 'INAPPROPRIATE_BEHAVIOR', 'OTHER', 'DRIVER_MISMATCH'
    )),
    -- Toan ven cac field ket luan theo status
    CONSTRAINT ck_dispute_resolution_fields CHECK (
        (status IN ('OPEN', 'INVESTIGATING')
            AND resolution_amount IS NULL AND resolution_note IS NULL
            AND resolved_by IS NULL AND resolved_at IS NULL)
     OR (status IN ('RESOLVED_REFUND', 'RESOLVED_DEDUCT')
            AND resolution_amount IS NOT NULL AND resolution_amount > 0
            AND resolution_note IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
     OR (status = 'CLOSED_NO_FAULT'
            AND resolution_amount IS NULL AND resolution_note IS NOT NULL
            AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX idx_dispute_pending          ON dispute (created_at ASC, id ASC) WHERE status IN ('OPEN', 'INVESTIGATING');
CREATE INDEX idx_dispute_history          ON dispute (created_at DESC, id DESC);
CREATE INDEX idx_dispute_customer_history ON dispute (customer_id, created_at DESC);
CREATE INDEX idx_dispute_driver_history   ON dispute (driver_id, created_at DESC);
-- V34: scheduled job quet cac khoan khau tru qua han
CREATE INDEX idx_dispute_deduct_deadline  ON dispute (deduct_deadline) WHERE pending_deduct_shortfall IS NOT NULL;

CREATE TRIGGER trg_dispute_updated_at
    BEFORE UPDATE ON dispute
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  dispute        IS 'Khieu nai don hang (spec #010); quyet dinh terminal bat bien.';
COMMENT ON COLUMN dispute.status IS 'OPEN | INVESTIGATING | RESOLVED_REFUND | RESOLVED_DEDUCT | CLOSED_NO_FAULT.';
