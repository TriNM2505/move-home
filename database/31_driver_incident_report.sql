-- =============================================================================
-- Bang: driver_incident_report  (31/32)
-- Tac dung: Bao SU CO cua Tai xe giua chuyen (hong xe / ly do bat ngo) khi don o
--           ACCEPTED hoac IN_PROGRESS. Manager xac nhan -> ban don lai pool
--           (CONFIRMED, driver_id NULL) cho tai xe khac; qua 15 phut khong ai nhan
--           -> hoan coc 30% + boi thuong 200k cho khach (200k tru vao tai xe gay su co).
-- Nguon migration: V44.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-08 (NUMERIC(15,0)), AC-13 (tien ghi transaction),
--               AC-14 (VARCHAR+CHECK), HR-21 (khong reserved word).
-- Phu thuoc: service_order, app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE driver_incident_report (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id              UUID          NOT NULL REFERENCES service_order(id),
    driver_id             UUID          NOT NULL REFERENCES app_user(id),
    reason                VARCHAR(500)  NOT NULL,
    order_status_snapshot VARCHAR(30)   NOT NULL CHECK (order_status_snapshot IN ('ACCEPTED', 'IN_PROGRESS')),

    status                VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN (
                              'PENDING',              -- vua bao, cho Manager xac nhan
                              'CONFIRMED',            -- Manager xac nhan, da ban lai pool
                              'RESOLVED_REASSIGNED',  -- da co tai xe khac nhan lai
                              'COMPENSATED'           -- qua 15p khong ai nhan -> hoan coc + boi thuong
                          )),
    reassign_deadline     TIMESTAMPTZ,               -- han 15p, set khi CONFIRMED

    confirmed_by          UUID          REFERENCES app_user(id),
    confirmed_at          TIMESTAMPTZ,

    refund_amount         NUMERIC(15,0) CHECK (refund_amount IS NULL OR refund_amount >= 0),   -- coc 30% + 200k ve customer_wallet
    penalty_amount        NUMERIC(15,0) CHECK (penalty_amount IS NULL OR penalty_amount >= 0), -- 200k tru vi tai xe
    compensated_by        UUID          REFERENCES app_user(id),
    compensated_at        TIMESTAMPTZ,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_incident_report PRIMARY KEY (id)
);

-- Toi da 1 su co DANG MO tren 1 don tai 1 thoi diem (cho phep nhieu su co theo vong doi)
CREATE UNIQUE INDEX uq_driver_incident_open_per_order ON driver_incident_report (order_id) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE INDEX idx_driver_incident_pending   ON driver_incident_report (created_at ASC, id ASC) WHERE status = 'PENDING';
CREATE INDEX idx_driver_incident_confirmed ON driver_incident_report (reassign_deadline ASC, id ASC) WHERE status = 'CONFIRMED';
CREATE INDEX idx_driver_incident_driver    ON driver_incident_report (driver_id, created_at DESC, id DESC);

CREATE TRIGGER trg_driver_incident_report_updated_at
    BEFORE UPDATE ON driver_incident_report
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  driver_incident_report               IS 'Bao su co tai xe giua chuyen. Manager xac nhan -> ban don lai pool; qua 15p -> hoan coc + boi thuong 200k.';
COMMENT ON COLUMN driver_incident_report.penalty_amount IS 'So tien tru vi tai xe gay su co khi COMPENSATED (200k). Cong ty chiu phan coc.';
