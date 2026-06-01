-- =============================================================================
-- V4: Tao bang driver_profile — Ho so bo sung cua tai xe (1-1 voi app_user)
-- Spec #001 FR-050..FR-058 (upload giay to), FR-064..FR-068 (Manager duyet).
-- AC-08: deposit_amount, total_revenue dung NUMERIC(15,0) — VND nguyen dong.
-- Ham update_updated_at_column() da tao o V1 — dung lai o day.
-- =============================================================================

CREATE TABLE driver_profile (

    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- FK 1-1 toi app_user — chi cho user co role='DRIVER'
    -- UNIQUE: mot User chi co toi da 1 driver_profile
    -- CASCADE: xoa User → xoa profile (Driver bi hard-delete, hiem gap)
    user_id                 UUID            NOT NULL
                            REFERENCES app_user(id) ON DELETE CASCADE,

    -- Thong tin bang lai xe — lay tu document GPLX (Step 2 onboarding)
    license_number          VARCHAR(50)     UNIQUE,      -- So GPLX, unique toan he thong
    license_class           VARCHAR(10),                 -- Hang lai xe: B2, C, D, E, F

    -- Thong tin xe — lay tu document VEHICLE_REGISTRATION (Step 2 onboarding)
    vehicle_plate           VARCHAR(20),                 -- Bien so xe, vd: 30A-12345
    vehicle_type            VARCHAR(50),                 -- Mo ta loai xe cho Manager xem
    vehicle_capacity_kg     INTEGER,                     -- Tai trong toi da (kg), chi de hien thi

    -- Coc Driver: 3.000.000 VND dong khi dang ky, la COLLATERAL cho DamageReport
    -- NUMERIC(15,0): VND nguyen dong, khong co xu (AC-08)
    -- CHECK constraint: dep >= 0 (Constitution HR-18 tuong tu wallet balance)
    deposit_amount          NUMERIC(15,0)   NOT NULL DEFAULT 0
                            CHECK (deposit_amount >= 0),
    deposit_paid_at         TIMESTAMPTZ,                 -- NULL = chua dong coc

    -- Audit duyet: Manager nao duyet va khi nao
    approved_at             TIMESTAMPTZ,                 -- NULL = chua duyet hoac bi REJECTED
    approved_by_manager_id  UUID
                            REFERENCES app_user(id) ON DELETE SET NULL,

    -- Thong ke hieu suat Driver
    total_orders_completed  INTEGER         NOT NULL DEFAULT 0,
    total_revenue           NUMERIC(15,0)   NOT NULL DEFAULT 0
                            CHECK (total_revenue >= 0),
    -- Diem danh gia trung binh (0.00 - 5.00) — khong phai tien, dung NUMERIC(3,2)
    average_rating          NUMERIC(3,2)    NOT NULL DEFAULT 0.00
                            CHECK (average_rating >= 0 AND average_rating <= 5),

    -- Timestamps (AC-07: TIMESTAMPTZ = luu UTC)
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_profile PRIMARY KEY (id),
    CONSTRAINT uq_driver_profile_user_id UNIQUE (user_id)
);


-- Index tim theo user_id (query chinh khi load profile Driver)
CREATE INDEX idx_driver_profile_user_id
    ON driver_profile (user_id);

-- Index tim Driver da duoc duyet (bao cao, leaderboard)
CREATE INDEX idx_driver_profile_approved
    ON driver_profile (approved_at)
    WHERE approved_at IS NOT NULL;

-- Index cho bao cao top earning Driver (findTop5ByOrderByTotalRevenueDesc)
CREATE INDEX idx_driver_profile_revenue
    ON driver_profile (total_revenue DESC)
    WHERE total_revenue > 0;


-- Trigger tu dong cap nhat updated_at (ham tao tu V1)
CREATE TRIGGER trg_driver_profile_updated_at
    BEFORE UPDATE ON driver_profile
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMENT ON TABLE  driver_profile                IS 'Ho so Driver: xe, giay to, coc, thong ke. Quan he 1-1 voi app_user (role=DRIVER).';
COMMENT ON COLUMN driver_profile.deposit_amount IS 'Tien coc hien tai (VND, min=0). Tru khi co DamageReport, nap lai khi Driver muon tiep tuc.';
COMMENT ON COLUMN driver_profile.total_revenue  IS 'Tong thu nhap da nhan (VND). Cong len sau moi don COMPLETED + het escrow 2h.';
COMMENT ON COLUMN driver_profile.average_rating IS '0.00–5.00. Cap nhat sau moi Customer danh gia post-COMPLETED.';
