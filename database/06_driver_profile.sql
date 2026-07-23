-- =============================================================================
-- Bang: driver_profile  (06/32)
-- Tac dung: Ho so bo sung cua Tai xe (quan he 1-1 voi app_user role=DRIVER). Luu
--           thong tin bang lai + xe, tien coc 3 trieu (collateral cho DamageReport),
--           moc duyet cua Manager, va cac chi so hieu suat (so don, doanh thu,
--           rating trung binh) da denormalize de dashboard nhanh.
-- Nguon migration: V4 (goc) + V10 (onboarding fields + CHECK license_class)
--                  + V15 (unique vehicle_plate) + V40 (default rating 5.00).
-- Constitution: AC-08 (tien NUMERIC(15,0)), HR-18 (coc >= 0), AC-07 (TIMESTAMPTZ).
-- Phu thuoc: ham update_updated_at_column() (tao o 01_app_user.sql).
-- =============================================================================

CREATE TABLE driver_profile (
    id                       UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id                  UUID            NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,

    -- Bang lai xe
    license_number           VARCHAR(50)     UNIQUE,
    license_class            VARCHAR(10),
    license_expiry_date      DATE,                       -- V10

    -- Xe
    vehicle_plate            VARCHAR(20),
    vehicle_type             VARCHAR(50),
    vehicle_capacity_kg      INTEGER,

    -- Coc Driver (collateral) — VND nguyen dong, >= 0 (AC-08, HR-18)
    deposit_amount           NUMERIC(15,0)   NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    deposit_paid_at          TIMESTAMPTZ,

    -- Duyet onboarding
    approved_at              TIMESTAMPTZ,
    approved_by_manager_id   UUID            REFERENCES app_user(id) ON DELETE SET NULL,
    onboarding_completed_at  TIMESTAMPTZ,                -- V10
    last_reminder_at         TIMESTAMPTZ,                -- V10

    -- Thong ke hieu suat
    total_orders_completed   INTEGER         NOT NULL DEFAULT 0,
    total_revenue            NUMERIC(15,0)   NOT NULL DEFAULT 0 CHECK (total_revenue >= 0),
    -- Rating 0.00-5.00; default 5.00 khi chua co danh gia (V40)
    average_rating           NUMERIC(3,2)    NOT NULL DEFAULT 5.00
                             CHECK (average_rating >= 0 AND average_rating <= 5),

    profile_version          BIGINT          NOT NULL DEFAULT 0,   -- V10: optimistic lock

    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_profile             PRIMARY KEY (id),
    CONSTRAINT uq_driver_profile_user_id     UNIQUE (user_id),
    CONSTRAINT uq_driver_profile_vehicle_plate UNIQUE (vehicle_plate),          -- V15
    CONSTRAINT ck_driver_profile_license_class                                   -- V10
        CHECK (license_class IS NULL OR license_class IN ('B1', 'B2', 'C', 'D'))
);

CREATE INDEX idx_driver_profile_user_id  ON driver_profile (user_id);
CREATE INDEX idx_driver_profile_approved ON driver_profile (approved_at) WHERE approved_at IS NOT NULL;
CREATE INDEX idx_driver_profile_revenue  ON driver_profile (total_revenue DESC) WHERE total_revenue > 0;

CREATE TRIGGER trg_driver_profile_updated_at
    BEFORE UPDATE ON driver_profile
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  driver_profile                IS 'Ho so Driver: xe, giay to, coc, thong ke. 1-1 voi app_user (role=DRIVER).';
COMMENT ON COLUMN driver_profile.deposit_amount IS 'Coc hien tai (VND, >=0). Tru khi co DamageReport; nap lai de tiep tuc.';
COMMENT ON COLUMN driver_profile.average_rating IS '0.00-5.00. Mac dinh 5.00 khi chua co danh gia (V40).';
