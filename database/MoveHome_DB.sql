-- #############################################################################
-- #  MoveHome_DB.sql — TOAN BO SCHEMA DATABASE Move_home (PostgreSQL 16)       #
-- #                                                                            #
-- #  Marketplace dich vu chuyen nha noi thanh Ha Noi (SWP @ FPT).              #
-- #  File nay HOP NHAT 32 bang tu 32 file rieng (01..32) theo dung thu tu      #
-- #  phu thuoc FK -> co the chay 1 lan tren 1 DB rong.                         #
-- #                                                                            #
-- #  ⚠️ DAY LA BAN THAM KHAO (documentation). Nguon schema THAT chay tren      #
-- #     Neon la Flyway migration V1..V44 (backend tu ap khi khoi dong).        #
-- #     KHONG chay file nay len DB dung chung — xem README.md.                 #
-- #                                                                            #
-- #  Constitution: AC-07 (TIMESTAMPTZ) | AC-08 (tien NUMERIC(15,0))            #
-- #                AC-09 (soft delete) | AC-14 (VARCHAR+CHECK, khong ENUM)     #
-- #                HR-21 (tranh reserved word) | AC-12 (Flyway)                #
-- #                                                                            #
-- #  Thu tu tao bang (dependency-safe):                                        #
-- #    app_user -> token/login -> driver_profile/document -> service_order     #
-- #    -> rating/location -> wallet -> withdrawal -> dispute* -> transaction    #
-- #    -> commission* -> chat -> notification/audit -> cancellation* -> blog*   #
-- #    -> incident*                                                            #
-- #############################################################################


BEGIN;


-- >>> Nguon: database/01_app_user.sql
-- =============================================================================
-- Bang: app_user  (01/32)
-- Tac dung: Bang nguoi dung loi cho CA 4 vai tro (Customer / Driver / Manager /
--           Admin) trong 1 bang duy nhat. Giu danh tinh, mat khau (BCrypt),
--           trang thai tai khoan + onboarding, khoa tai khoan (HR-16), dinh chi
--           (suspend), avatar, va cac truong rieng cua Driver (operating_districts).
-- Nguon migration: V1 (goc) + V23 (them status LOCKED) + V27 (cot suspend)
--                  + V33 (avatar). Day la schema HOP NHAT trang thai cuoi cung.
-- Constitution: HR-02 (BCrypt), HR-10 (RBAC role), HR-16 (lockout),
--               HR-21 (ten "app_user" tranh reserved word "user"),
--               AC-07 (TIMESTAMPTZ), AC-09 (soft delete deleted_at), AC-14 (VARCHAR+CHECK).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Ham dung chung: tu dong set updated_at = NOW() truoc moi UPDATE.
-- Tao 1 lan o day; cac bang khac (driver_profile, service_order, dispute, ...)
-- dung lai ham nay cho trigger cua minh.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TABLE app_user (
    id                          UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Phan quyen: 4 vai tro co dinh (HR-10)
    role                        VARCHAR(20)     NOT NULL
                                CHECK (role IN ('CUSTOMER', 'DRIVER', 'MANAGER', 'ADMIN')),

    -- Trang thai tai khoan + onboarding + khoa (LOCKED them o V23)
    status                      VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFY'
                                CHECK (status IN (
                                    'ACTIVE',
                                    'LOCKED',              -- Admin khoa tai khoan
                                    'PENDING_VERIFY',      -- vua dang ky, chua verify email
                                    'PENDING_DOCUMENTS',   -- Driver da verify, chua upload giay to
                                    'PENDING_DEPOSIT',     -- Driver da co giay to, chua coc 3tr
                                    'PENDING_APPROVAL',    -- Driver da coc, cho Manager duyet
                                    'SUSPENDED',           -- bi dinh chi (DamageReport het coc/vi hoac Admin)
                                    'REJECTED'             -- Driver bi tu choi, co the re-submit
                                )),

    -- Danh tinh (dung chung moi vai tro)
    email                       VARCHAR(255)    NOT NULL,
    phone                       VARCHAR(20),
    password_hash               VARCHAR(255)    NOT NULL,   -- BCrypt cost 12 (HR-02) — KHONG plaintext
    full_name                   VARCHAR(100)    NOT NULL,
    username                    VARCHAR(30),                -- chi Customer dung; NULL cho Driver/Staff

    date_of_birth               DATE,
    address                     VARCHAR(500),

    -- Rieng cho DRIVER (NULL cho vai tro khac)
    operating_districts         TEXT[],                     -- quan hoat dong (1-12 quan Ha Noi)
    rejection_reason            TEXT,                       -- ly do bi REJECTED

    email_verified              BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Dieu khoan su dung
    terms_accepted              BOOLEAN         NOT NULL DEFAULT FALSE,
    terms_accepted_at           TIMESTAMPTZ,

    -- Bao mat dang nhap
    must_change_password        BOOLEAN         NOT NULL DEFAULT FALSE,  -- Manager/Admin lan dau (FR-036)
    failed_login_count          INTEGER         NOT NULL DEFAULT 0,      -- dem sai password (HR-16)
    last_failed_login_at        TIMESTAMPTZ,
    locked_until                TIMESTAMPTZ,                             -- NULL = khong bi khoa (HR-16)

    -- Avatar (V33) — upload qua Cloudinary (AC-10)
    avatar_url                  VARCHAR(500),
    avatar_public_id            VARCHAR(255),

    -- Dinh chi tai khoan (V27) — nguon su that cho Admin suspend/reactivate
    suspension_previous_status  VARCHAR(30),
    suspended_at                TIMESTAMPTZ,
    suspended_by                UUID            REFERENCES app_user(id) ON DELETE SET NULL,
    suspension_reason           TEXT,
    suspension_until            TIMESTAMPTZ,

    -- Audit + soft delete
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ,    -- AC-09: NULL = con hieu luc; KHONG dung DELETE

    CONSTRAINT pk_app_user       PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_username UNIQUE (username),

    -- Toan ven cac cot dinh chi theo status (V27)
    CONSTRAINT ck_app_user_suspension_fields CHECK (
        (
            status = 'SUSPENDED'
            AND suspended_at IS NOT NULL
            AND suspended_by IS NOT NULL
            AND suspension_reason IS NOT NULL
            AND suspension_previous_status IS NOT NULL
        )
        OR
        (
            status <> 'SUSPENDED'
            AND suspended_at IS NULL
            AND suspended_by IS NULL
            AND suspension_reason IS NULL
            AND suspension_until IS NULL
            AND suspension_previous_status IS NULL
        )
    )
);

CREATE INDEX idx_user_email_active ON app_user (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_role_status  ON app_user (role, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_locked_until ON app_user (locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX idx_user_created_at   ON app_user (created_at DESC);

CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  app_user               IS 'Nguoi dung: Customer/Driver/Manager/Admin trong 1 bang. Soft delete (deleted_at).';
COMMENT ON COLUMN app_user.role          IS 'CUSTOMER | DRIVER | MANAGER | ADMIN (HR-10).';
COMMENT ON COLUMN app_user.status        IS 'Trang thai tai khoan/onboarding. LOCKED = Admin khoa; duyet Driver dung driver_profile.approved_at.';
COMMENT ON COLUMN app_user.password_hash IS 'BCrypt cost 12. KHONG bao gio luu plaintext (HR-02).';
COMMENT ON COLUMN app_user.deleted_at    IS 'Soft delete (AC-09). KHONG dung DELETE FROM app_user.';


-- >>> Nguon: database/02_email_verification_token.sql
-- =============================================================================
-- Bang: email_verification_token  (02/32)
-- Tac dung: Luu token xac thuc email (dang SHA-256 hash, het han 24h) cho luong
--           Customer/Driver dang ky. Xac minh email bang cach hash token nhan tu
--           link roi so khop; danh dau used_at chong dung lai.
-- Nguon migration: V2.
-- Constitution: HR-02 pattern (khong luu raw token), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE email_verification_token (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token      VARCHAR(100) NOT NULL,        -- SHA-256 hex cua raw token gui trong email
    expires_at TIMESTAMPTZ  NOT NULL,        -- het han sau 24h
    used_at    TIMESTAMPTZ,                  -- NULL = chua dung; co gia tri = da verify
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_verification_token PRIMARY KEY (id),
    CONSTRAINT uq_evtoken_token            UNIQUE (token)
);

CREATE INDEX idx_evtoken_token       ON email_verification_token (token);
CREATE INDEX idx_evtoken_user_active ON email_verification_token (user_id, expires_at) WHERE used_at IS NULL;

COMMENT ON TABLE  email_verification_token       IS 'Token xac thuc email (hash, het han 24h). Xoa khi tao token moi (FR-008).';
COMMENT ON COLUMN email_verification_token.token IS 'SHA-256 hex cua raw token gui trong email. KHONG phai raw token.';


-- >>> Nguon: database/03_refresh_token.sql
-- =============================================================================
-- Bang: refresh_token  (03/32)
-- Tac dung: Luu refresh token server-side (dang SHA-256 hash) cho JWT. Ho tro
--           rotation (moi lan dung cap token moi, revoke token cu) va reuse
--           detection (token da revoke ma bi dung lai -> PANIC, revoke toan bo).
-- Nguon migration: V3.
-- Constitution: AC-03 (JWT refresh 7 ngay + rotation + luu DB), HR-02 pattern.
-- =============================================================================

CREATE TABLE refresh_token (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash           VARCHAR(64) NOT NULL,     -- SHA-256 hex
    expires_at           TIMESTAMPTZ NOT NULL,     -- het han sau 7 ngay (AC-03)
    revoked_at           TIMESTAMPTZ,              -- NULL = con hieu luc
    replaced_by_token_id UUID        REFERENCES refresh_token(id) ON DELETE SET NULL,  -- chain rotation
    user_agent           VARCHAR(500),
    ip_address           VARCHAR(45),              -- IPv4/IPv6
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_token      PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_rt_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_rt_user_active ON refresh_token (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_expires_at  ON refresh_token (expires_at) WHERE revoked_at IS NOT NULL;

COMMENT ON TABLE  refresh_token                      IS 'Refresh token luu server-side (hash). Rotation + revoke khi logout (AC-03).';
COMMENT ON COLUMN refresh_token.replaced_by_token_id IS 'Token moi thay the sau rotation. Dung detect reuse attack (FR-029).';


-- >>> Nguon: database/04_password_reset_token.sql
-- =============================================================================
-- Bang: password_reset_token  (04/32)
-- Tac dung: Luu token dat lai mat khau (dang hash) cho luong "Quen mat khau" qua
--           email. Xac minh bang cach hash token nhan tu link roi so khop; danh
--           dau used_at khi da doi mat khau xong.
-- Nguon migration: V19.
-- Constitution: HR-02 pattern (luu hash), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE password_reset_token (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_password_reset_token PRIMARY KEY (id)
);

COMMENT ON TABLE password_reset_token IS 'Token dat lai mat khau (luu hash, khong luu token tho).';


-- >>> Nguon: database/05_login_event.sql
-- =============================================================================
-- Bang: login_event  (05/32)
-- Tac dung: Ghi lai moi lan dang nhap thanh cong cua nguoi dung, phuc vu bao cao
--           hoat dong khach hang (DAU/MAU) va analytics cho Admin.
-- Nguon migration: V26.
-- Constitution: AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE login_event (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    logged_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_login_event PRIMARY KEY (id),
    CONSTRAINT fk_login_event_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_login_event_logged_in_at_user ON login_event (logged_in_at, user_id);
CREATE INDEX idx_login_event_user_logged_in_at ON login_event (user_id, logged_in_at);

COMMENT ON TABLE login_event IS 'Su kien dang nhap thanh cong, phuc vu bao cao hoat dong khach hang.';


-- >>> Nguon: database/06_driver_profile.sql
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


-- >>> Nguon: database/07_driver_document.sql
-- =============================================================================
-- Bang: driver_document  (07/32)
-- Tac dung: Luu tai lieu onboarding cua Tai xe (GPLX truoc/sau, dang ky xe
--           truoc/sau, anh xe truoc/sau/ngang, anh chan dung). Anh upload qua
--           Cloudinary signed upload; public_id dung de ky signed URL TTL 1h.
-- Nguon migration: V14 (goc, 3 loai) + V29 (public_id) + V31/V32 (mo rong len 11 loai).
-- Constitution: AC-10 (Cloudinary signed), AC-14 (VARCHAR+CHECK), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE driver_document (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    driver_id   UUID         NOT NULL REFERENCES app_user(id),
    doc_type    VARCHAR(40)  NOT NULL
                CHECK (doc_type IN (
                    -- 8 loai chuan hien tai (V32)
                    'DRIVING_LICENSE_FRONT',
                    'DRIVING_LICENSE_BACK',
                    'VEHICLE_REGISTRATION_FRONT',
                    'VEHICLE_REGISTRATION_BACK',
                    'VEHICLE_PHOTO_FRONT',
                    'VEHICLE_PHOTO_REAR',
                    'VEHICLE_PHOTO_SIDE',
                    'FACE_PHOTO',
                    -- 3 loai cu (giu cho du lieu lich su truoc V32)
                    'DRIVING_LICENSE',
                    'VEHICLE_REGISTRATION',
                    'VEHICLE_PHOTO'
                )),
    url         VARCHAR(500) NOT NULL,
    public_id   VARCHAR(255),                 -- V29: Cloudinary public_id (ky signed URL); NULL cho tai lieu cu
    uploaded_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_document PRIMARY KEY (id)
);

CREATE INDEX idx_driver_document_driver_id ON driver_document (driver_id);

COMMENT ON TABLE  driver_document          IS 'Tai lieu onboarding tai xe (Cloudinary). 11 loai doc_type (8 chuan + 3 legacy).';
COMMENT ON COLUMN driver_document.public_id IS 'Cloudinary public_id, ky signed URL TTL 1h (spec 008). Nullable cho tai lieu cu.';


-- >>> Nguon: database/08_service_order.sql
-- =============================================================================
-- Bang: service_order  (08/32)
-- Tac dung: DON HANG CHUYEN NHA — thuc the trung tam. Giu diem di/den, gio hen,
--           loai xe + so boc xep, breakdown gia (base + phu thu + porter),
--           snapshot ty le commission, trang thai vong doi (11 gia tri), va cac
--           moc thoi gian (bat dau/den noi/hoan thanh/tra not 70%/release escrow).
-- Nguon migration: V5 (goc) + V7 (booking + breakdown gia) + V16/V21 (status)
--                  + V25 (started_at) + V30 (2-phase payment + escrow, noi VARCHAR(30))
--                  + V37 (arrived_at). Cot vi tri tam V17 da bi V28 xoa (khong co o day).
-- Constitution: HR-21 (ten tranh reserved word "order"), AC-07 (TIMESTAMPTZ),
--               AC-08 (tien NUMERIC(15,0)), AC-09 (soft delete), AC-14 (VARCHAR+CHECK).
-- Phu thuoc: app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE service_order (
    id                          UUID            NOT NULL DEFAULT gen_random_uuid(),
    order_code                  VARCHAR(20)     NOT NULL,           -- vd "MH202606010001"

    customer_id                 UUID            NOT NULL REFERENCES app_user(id),
    driver_id                   UUID            REFERENCES app_user(id),   -- NULL khi chua co Driver

    -- Diem di / den
    pickup_address              VARCHAR(500)    NOT NULL,
    pickup_district             VARCHAR(100),
    dropoff_address             VARCHAR(500)    NOT NULL,
    dropoff_district            VARCHAR(100),
    pickup_lat                  NUMERIC(10,7),                       -- V7
    pickup_lng                  NUMERIC(10,7),                       -- V7
    dropoff_lat                 NUMERIC(10,7),                       -- V7
    dropoff_lng                 NUMERIC(10,7),                       -- V7
    pickup_floor                INTEGER,                             -- V7
    pickup_has_elevator         BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7
    pickup_has_alley            BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7
    dropoff_floor               INTEGER,                             -- V7
    dropoff_has_elevator        BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7
    dropoff_has_alley           BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7

    scheduled_at                TIMESTAMPTZ     NOT NULL,            -- gio hen (check peak-hour AC-07)

    -- Loai xe + boc xep (V7)
    vehicle_type                VARCHAR(20)     NOT NULL DEFAULT 'TRUCK_500KG',
    porter_count                INTEGER         NOT NULL DEFAULT 0,

    -- Trang thai vong doi (V21: 11 gia tri; con lan cap legacy + moi)
    status                      VARCHAR(30)     NOT NULL DEFAULT 'PENDING',   -- noi VARCHAR(30) o V30

    -- Gia (AC-08)
    total_quote                 NUMERIC(15,0)   NOT NULL,
    commission_rate_snapshot    NUMERIC(5,4)    NOT NULL DEFAULT 0.3000,   -- snapshot luc tao don
    base_fare                   NUMERIC(15,0),                       -- V7
    peak_surcharge              NUMERIC(15,0)   DEFAULT 0,           -- V7
    alley_surcharge             NUMERIC(15,0)   DEFAULT 0,           -- V7
    floor_surcharge             NUMERIC(15,0)   DEFAULT 0,           -- V7
    porter_fee                  NUMERIC(15,0)   DEFAULT 0,           -- V7

    -- OSRM (NULL neu dung fallback bang quan->quan, AC-06)
    distance_km                 NUMERIC(6,2),
    estimated_duration_minutes  INTEGER,

    -- Moc thoi gian su kien
    started_at                  TIMESTAMPTZ,                         -- V25: khi -> IN_PROGRESS
    arrived_at                  TIMESTAMPTZ,                         -- V37: tai xe "Da den diem don"
    completed_at                TIMESTAMPTZ,                         -- khi -> COMPLETED (tinh escrow 2h)
    final_paid_at               TIMESTAMPTZ,                         -- V30: khach tra not 70% (VNPay IPN)
    earning_released_at         TIMESTAMPTZ,                         -- V30: release 70% vao vi tai xe
    cancelled_at                TIMESTAMPTZ,
    cancellation_reason         TEXT,

    notes                       TEXT,                                -- ghi chu Customer cho Driver

    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ,                         -- soft delete (AC-09)

    CONSTRAINT pk_service_order          PRIMARY KEY (id),
    CONSTRAINT uq_service_order_code     UNIQUE (order_code),
    CONSTRAINT ck_service_order_vehicle_type                                   -- V7
        CHECK (vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')),
    CONSTRAINT ck_service_order_status                                         -- V21
        CHECK (status IN (
            'PENDING',
            'PENDING_PAYMENT',
            'CONFIRMED',
            'ASSIGNED',
            'ACCEPTED',
            'IN_PROGRESS',
            'AWAITING_FINAL_PAYMENT',
            'COMPLETED',
            'DISPUTED',              -- alias legacy cua IN_DISPUTE
            'IN_DISPUTE',
            'CANCELLED'
        ))
);

CREATE INDEX idx_order_code         ON service_order (order_code);
CREATE INDEX idx_order_customer     ON service_order (customer_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_driver       ON service_order (driver_id, created_at DESC) WHERE deleted_at IS NULL AND driver_id IS NOT NULL;
CREATE INDEX idx_order_status       ON service_order (status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_completed_at ON service_order (completed_at DESC) WHERE status = 'COMPLETED' AND deleted_at IS NULL;
-- V30: scheduled job escrow quet don COMPLETED chua release qua 2h
CREATE INDEX idx_order_escrow_pending ON service_order (completed_at)
    WHERE status = 'COMPLETED' AND earning_released_at IS NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_service_order_updated_at
    BEFORE UPDATE ON service_order
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  service_order                          IS 'Don hang chuyen nha. Ten "service_order" tranh reserved word "order" (HR-21).';
COMMENT ON COLUMN service_order.total_quote              IS 'Tong bao gia (VND): base + phu thu + porter. NUMERIC(15,0) — AC-08.';
COMMENT ON COLUMN service_order.commission_rate_snapshot IS 'Ty le commission luc tao don; khong doi du Admin doi rate sau (dashboard FR-006).';
COMMENT ON COLUMN service_order.status                   IS '11 gia tri (V21). Con lan cap legacy/moi: PENDING~PENDING_PAYMENT, ASSIGNED~ACCEPTED, DISPUTED~IN_DISPUTE.';


-- >>> Nguon: database/09_order_rating.sql
-- =============================================================================
-- Bang: order_rating  (09/32)
-- Tac dung: Danh gia (1-5 sao + nhan xet) cua Customer cho Tai xe sau khi don
--           COMPLETED. Moi don chi duoc danh gia 1 lan (order_id UNIQUE); dung
--           de tinh average_rating cua driver_profile.
-- Nguon migration: V9.
-- Constitution: AC-14 (INTEGER + CHECK), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE order_rating (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL UNIQUE REFERENCES service_order(id),   -- 1 danh gia / don
    customer_id UUID        NOT NULL REFERENCES app_user(id),
    driver_id   UUID        REFERENCES app_user(id),                        -- NULL cho du lieu cu
    stars       INTEGER     NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_rating PRIMARY KEY (id)
);

CREATE INDEX idx_order_rating_driver ON order_rating (driver_id);

COMMENT ON TABLE  order_rating         IS 'Danh gia cua Customer cho don da hoan thanh; nguon tinh average_rating.';
COMMENT ON COLUMN order_rating.order_id IS 'Don duoc danh gia; UNIQUE de moi don chi 1 danh gia.';


-- >>> Nguon: database/10_driver_location.sql
-- =============================================================================
-- Bang: driver_location  (10/32)
-- Tac dung: Luu VI TRI MOI NHAT cua moi Tai xe (1 dong/tai xe, UPSERT theo
--           driver_id). Customer poll de theo doi don dang giao. Thay cho cac cot
--           vi tri tam tren service_order (V17 -> da xoa o V28).
-- Nguon migration: V20.
-- Constitution: AC-07 (TIMESTAMPTZ). Spec #003/#006.
-- =============================================================================

CREATE TABLE driver_location (
    driver_id        UUID          NOT NULL REFERENCES app_user(id),
    current_order_id UUID          REFERENCES service_order(id),
    lat              NUMERIC(10,7) NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng              NUMERIC(10,7) NOT NULL CHECK (lng BETWEEN -180 AND 180),
    heading          NUMERIC(5,2)  CHECK (heading IS NULL OR (heading >= 0 AND heading < 360)),
    speed_kmh        NUMERIC(6,2)  CHECK (speed_kmh IS NULL OR (speed_kmh >= 0 AND speed_kmh <= 180)),
    recorded_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_location PRIMARY KEY (driver_id)
);

COMMENT ON TABLE driver_location IS 'Vi tri moi nhat cua tai xe (UPSERT theo driver_id) cho Customer theo doi don.';


-- >>> Nguon: database/11_driver_wallet.sql
-- =============================================================================
-- Bang: driver_wallet  (11/32)
-- Tac dung: Vi tong hop cua Tai xe (1-1). Giu so du kha dung (khong am), tong da
--           kiem va tong da rut. Cong tien khi don COMPLETED + het escrow 2h; tru
--           khi co DamageReport hoac khi rut. So cai chi tiet nam o bang transaction.
-- Nguon migration: V11.
-- Constitution: AC-08 (NUMERIC(15,0)), HR-18 (balance >= 0), AC-07 (TIMESTAMPTZ).
-- Phu thuoc: ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE driver_wallet (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    driver_id       UUID          NOT NULL UNIQUE REFERENCES app_user(id),
    balance         NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),        -- HR-18: khong am
    total_earned    NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (total_earned >= 0),
    total_withdrawn NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (total_withdrawn >= 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_wallet PRIMARY KEY (id)
);

CREATE TRIGGER trg_driver_wallet_updated_at
    BEFORE UPDATE ON driver_wallet
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  driver_wallet         IS 'Vi tong hop tai xe; giao dich chi tiet ghi append-only o bang transaction.';
COMMENT ON COLUMN driver_wallet.balance IS 'So du kha dung (VND, >=0) — HR-18.';


-- >>> Nguon: database/12_customer_wallet.sql
-- =============================================================================
-- Bang: customer_wallet  (12/32)
-- Tac dung: Vi tong hop cua Khach hang (1-1). Giu so du kha dung (khong am) va
--           cac so lieu tong (da nap / da chi / da rut). Nhan tien hoan coc khi
--           huy don hoac boi thuong su co tai xe; co the rut ve ngan hang.
--           (Governance: pham vi vi Customer dang cho leader duyet — spec #021.)
-- Nguon migration: V8 (goc) + V39 (them total_withdrawn).
-- Constitution: AC-08 (NUMERIC(15,0)), HR-18 (balance >= 0), AC-07 (TIMESTAMPTZ).
-- Phu thuoc: ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE customer_wallet (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    customer_id     UUID          NOT NULL UNIQUE REFERENCES app_user(id),
    balance         NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),       -- HR-18
    total_topped_up NUMERIC(15,0) NOT NULL DEFAULT 0,
    total_spent     NUMERIC(15,0) NOT NULL DEFAULT 0,
    total_withdrawn NUMERIC(15,0) NOT NULL DEFAULT 0,                            -- V39
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_customer_wallet PRIMARY KEY (id)
);

CREATE TRIGGER trg_customer_wallet_updated_at
    BEFORE UPDATE ON customer_wallet
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  customer_wallet         IS 'Vi tong hop khach hang; giao dich chi tiet ghi append-only o bang transaction.';
COMMENT ON COLUMN customer_wallet.balance IS 'So du kha dung (VND, >=0) — HR-18.';


-- >>> Nguon: database/13_withdrawal_request.sql
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


-- >>> Nguon: database/14_customer_withdrawal_request.sql
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


-- >>> Nguon: database/15_dispute.sql
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


-- >>> Nguon: database/16_dispute_evidence.sql
-- =============================================================================
-- Bang: dispute_evidence  (16/32)
-- Tac dung: Bang chung (anh/tai lieu) dinh kem 1 khieu nai, do cac ben upload
--           (Customer/Driver/Manager/Admin). Luu tren Cloudinary (public_id) +
--           metadata (content_type, kich thuoc). Dung dung timeline cua dispute.
-- Nguon migration: V16.
-- Constitution: AC-10 (Cloudinary), AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK).
-- =============================================================================

CREATE TABLE dispute_evidence (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    dispute_id           UUID         NOT NULL REFERENCES dispute(id),
    uploader_id          UUID         NOT NULL REFERENCES app_user(id),
    uploader_role        VARCHAR(20)  NOT NULL CHECK (uploader_role IN ('CUSTOMER', 'DRIVER', 'MANAGER', 'ADMIN')),
    evidence_type        VARCHAR(30)  NOT NULL CHECK (evidence_type IN ('PHOTO', 'DOCUMENT', 'OTHER')),
    cloudinary_public_id TEXT         NOT NULL,
    content_type         VARCHAR(100) NOT NULL,
    file_size_bytes      BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_evidence PRIMARY KEY (id)
);

CREATE INDEX idx_dispute_evidence_timeline ON dispute_evidence (dispute_id, created_at ASC, id ASC);

COMMENT ON TABLE dispute_evidence IS 'Bang chung khieu nai (Cloudinary) do cac ben upload theo timeline.';


-- >>> Nguon: database/17_dispute_comment.sql
-- =============================================================================
-- Bang: dispute_comment  (17/32)
-- Tac dung: Binh luan noi bo cua Manager/Admin trong qua trinh xu ly 1 khieu nai
--           (nhat ky trao doi). Co idempotency_key chong tao trung khi retry.
-- Nguon migration: V16.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK).
-- =============================================================================

CREATE TABLE dispute_comment (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    dispute_id      UUID        NOT NULL REFERENCES dispute(id),
    author_id       UUID        NOT NULL REFERENCES app_user(id),
    author_role     VARCHAR(20) NOT NULL CHECK (author_role IN ('MANAGER', 'ADMIN')),
    comment         TEXT        NOT NULL,
    idempotency_key UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_comment PRIMARY KEY (id),
    CONSTRAINT uq_dispute_comment_idempotency UNIQUE (author_id, idempotency_key)
);

CREATE INDEX idx_dispute_comment_timeline ON dispute_comment (dispute_id, created_at ASC, id ASC);

COMMENT ON TABLE dispute_comment IS 'Binh luan noi bo Manager/Admin khi xu ly khieu nai.';


-- >>> Nguon: database/18_dispute_photo.sql
-- =============================================================================
-- Bang: dispute_photo  (18/32)
-- Tac dung: Anh bang chung Khach dinh kem KHI TAO khieu nai (toi da 3 anh/khieu
--           nai, enforce o service). Luu Cloudinary signed upload (resource_type
--           authenticated); public_id de ky signed URL khi Manager xem.
-- Nguon migration: V35.
-- Constitution: AC-10 (Cloudinary signed, khong luu BLOB), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE dispute_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    dispute_id          UUID         NOT NULL REFERENCES dispute(id),
    url                 VARCHAR(500) NOT NULL,        -- secure_url Cloudinary
    public_id           VARCHAR(255) NOT NULL,        -- de ky signed URL + xoa asset
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_photo PRIMARY KEY (id)
);

CREATE INDEX idx_dispute_photo_dispute ON dispute_photo (dispute_id, uploaded_at);

COMMENT ON TABLE dispute_photo IS 'Anh bang chung khach dinh kem khieu nai; Cloudinary signed upload (AC-10).';


-- >>> Nguon: database/19_transaction.sql
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


-- >>> Nguon: database/20_commission_settings.sql
-- =============================================================================
-- Bang: commission_settings  (20/32)
-- Tac dung: Cau hinh gia/hoa hong ACTIVE dang SINGLETON (chi 1 dong, id=1). Giu
--           ty le commission, phu thu (peak/alley/floor JSONB), don gia/km theo
--           loai xe, phi boc xep, coc tai xe, muc rut toi thieu. Co optimistic
--           version; don cu giu commission_rate_snapshot rieng nen khong bi anh huong.
-- Nguon migration: V16.
-- Constitution: AC-08 (NUMERIC(15,0)/(5,4)), AC-07 (TIMESTAMPTZ). Spec #014.
-- =============================================================================

CREATE TABLE commission_settings (
    id                    INTEGER       NOT NULL DEFAULT 1 CHECK (id = 1),   -- singleton
    commission_rate       NUMERIC(5,4)  NOT NULL DEFAULT 0.3000 CHECK (commission_rate BETWEEN 0.0500 AND 0.5000),
    peak_surcharge_rate   NUMERIC(5,4)  NOT NULL DEFAULT 0.3000 CHECK (peak_surcharge_rate BETWEEN 0.0000 AND 1.0000),
    peak_hours            JSONB         NOT NULL DEFAULT '[{"start":"07:00","end":"09:00"},{"start":"17:00","end":"19:00"}]',
    alley_surcharge_rate  NUMERIC(5,4)  NOT NULL DEFAULT 0.2000 CHECK (alley_surcharge_rate BETWEEN 0.0000 AND 1.0000),
    floor_surcharge_tiers JSONB         NOT NULL DEFAULT '[{"min_floor":2,"max_floor":3,"rate":0.2000},{"min_floor":4,"max_floor":5,"rate":0.3000},{"min_floor":6,"max_floor":30,"rate":0.5000}]',
    base_rate_per_km      JSONB         NOT NULL DEFAULT '{"TRUCK_500KG":20000,"TRUCK_1T":30000,"TRUCK_15T":40000}',
    porter_fee_per_person JSONB         NOT NULL DEFAULT '{"TRUCK_500KG":150000,"TRUCK_1T":200000,"TRUCK_15T":300000}',
    driver_deposit_vnd    NUMERIC(15,0) NOT NULL DEFAULT 3000000 CHECK (driver_deposit_vnd BETWEEN 0 AND 50000000),
    min_withdrawal_vnd    NUMERIC(15,0) NOT NULL DEFAULT 100000  CHECK (min_withdrawal_vnd BETWEEN 50000 AND 1000000),
    version               BIGINT        NOT NULL DEFAULT 1 CHECK (version > 0),
    last_updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_updated_by       UUID          REFERENCES app_user(id),

    CONSTRAINT pk_commission_settings PRIMARY KEY (id)
);

-- Seed dong cau hinh mac dinh (id=1)
INSERT INTO commission_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE  commission_settings                 IS 'Cau hinh gia/hoa hong active singleton (id=1) co optimistic version (spec #014).';
COMMENT ON COLUMN commission_settings.commission_rate IS 'Ty le hoa hong cho quote/order MOI; order cu giu commission_rate_snapshot.';


-- >>> Nguon: database/21_commission_settings_history.sql
-- =============================================================================
-- Bang: commission_settings_history  (21/32)
-- Tac dung: Lich su thay doi cau hinh gia/hoa hong (append-only). Moi lan Admin
--           cap nhat commission_settings ghi 1 snapshot: version cu/moi, nguoi
--           thay doi, gia tri cu/moi va diff (JSONB) — phuc vu audit va doi soat.
-- Nguon migration: V16.
-- Constitution: AC-07 (TIMESTAMPTZ). Spec #014. Append-only (khong UPDATE/DELETE).
-- =============================================================================

CREATE TABLE commission_settings_history (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    from_version BIGINT        NOT NULL,
    to_version   BIGINT        NOT NULL,
    changed_by   UUID          NOT NULL REFERENCES app_user(id),
    old_values   JSONB         NOT NULL,
    new_values   JSONB         NOT NULL,
    diff         JSONB         NOT NULL,
    note         VARCHAR(1000),
    changed_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_commission_settings_history       PRIMARY KEY (id),
    CONSTRAINT uq_settings_history_to_version        UNIQUE (to_version),
    CONSTRAINT ck_settings_history_version_progression CHECK (to_version > from_version)
);

CREATE INDEX idx_settings_history_changed_at ON commission_settings_history (changed_at DESC, id DESC);

COMMENT ON TABLE commission_settings_history IS 'Lich su snapshot cau hinh gia/hoa hong (append-only) — audit + doi soat.';


-- >>> Nguon: database/22_conversation.sql
-- =============================================================================
-- Bang: conversation  (22/32)
-- Tac dung: Hoi thoai chat 3 cap (CUSTOMER_MANAGER / MANAGER_DRIVER /
--           CUSTOMER_DRIVER). Gan theo don (order_id); rieng CUSTOMER_MANAGER
--           voi order_id NULL la kenh ho tro chung (moi khach 1 thread). Giu
--           snapshot tin nhan cuoi de hien danh sach hoi thoai nhanh.
-- Nguon migration: V36.
-- Constitution: AC-05 (chat STOMP+SockJS), AC-14 (VARCHAR+CHECK), AC-07 (TIMESTAMPTZ),
--               HR-21 (khong reserved word).
-- =============================================================================

CREATE TABLE conversation (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID        REFERENCES service_order(id),
    type              VARCHAR(20) NOT NULL CHECK (type IN ('CUSTOMER_MANAGER', 'MANAGER_DRIVER', 'CUSTOMER_DRIVER')),
    customer_id       UUID        REFERENCES app_user(id),
    driver_id         UUID        REFERENCES app_user(id),
    last_message_text TEXT,
    last_message_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Moi don chi 1 hoi thoai/loai (chong tao trung)
CREATE UNIQUE INDEX uq_conversation_order_type ON conversation (order_id, type) WHERE order_id IS NOT NULL;
-- Kenh ho tro chung: moi khach chi 1 thread CUSTOMER_MANAGER (order_id NULL)
CREATE UNIQUE INDEX uq_conversation_support ON conversation (customer_id) WHERE type = 'CUSTOMER_MANAGER' AND order_id IS NULL;
CREATE INDEX idx_conversation_customer ON conversation (customer_id, last_message_at DESC);
CREATE INDEX idx_conversation_driver   ON conversation (driver_id, last_message_at DESC);
CREATE INDEX idx_conversation_type     ON conversation (type, last_message_at DESC);

COMMENT ON TABLE conversation IS 'Hoi thoai chat 3 cap; gan theo don hoac kenh ho tro chung (order_id NULL).';


-- >>> Nguon: database/23_chat_message.sql
-- =============================================================================
-- Bang: chat_message  (23/32)
-- Tac dung: Tin nhan trong 1 hoi thoai. Luu ben vung song song voi day realtime
--           WebSocket. Ho tro 1 anh/tin (image_public_id, hien qua signed URL);
--           read_at danh dau da doc (toi uu dem chua doc).
-- Nguon migration: V36 (goc) + V38 (image_public_id).
-- Constitution: AC-05 (chat), AC-10 (anh Cloudinary signed), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE chat_message (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversation(id),
    sender_id       UUID        NOT NULL REFERENCES app_user(id),
    content         TEXT        NOT NULL,           -- tin anh: content='' + image_public_id co gia tri
    image_public_id TEXT,                           -- V38: Cloudinary public_id (signed URL)
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_conversation ON chat_message (conversation_id, created_at);
CREATE INDEX idx_chat_message_unread       ON chat_message (conversation_id, sender_id) WHERE read_at IS NULL;

COMMENT ON TABLE  chat_message                 IS 'Tin nhan chat (luu DB + day WebSocket). 1 anh/tin qua image_public_id.';
COMMENT ON COLUMN chat_message.image_public_id IS 'Cloudinary public_id (authenticated) — hien qua signed URL (AC-10). NULL = tin text.';


-- >>> Nguon: database/24_notification.sql
-- =============================================================================
-- Bang: notification  (24/32)
-- Tac dung: Thong bao trong ung dung cho tung nguoi dung (chuong thong bao). Giu
--           loai, tieu de, noi dung va co doc chua. Bang toi gian (khong FK/CHECK/
--           index) — co chu y de nhe cho demo.
-- Nguon migration: V18.
-- Constitution: AC-07 (TIMESTAMPTZ). Spec #020.
-- =============================================================================

CREATE TABLE notification (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    type       VARCHAR(50) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE notification IS 'Thong bao trong ung dung theo tung user (chuong thong bao). Bang toi gian (spec #020).';


-- >>> Nguon: database/25_audit_log.sql
-- =============================================================================
-- Bang: audit_log  (25/32)
-- Tac dung: Nhat ky he thong (audit) append-only cho cac su kien bao mat + nghiep
--           vu quan trong (doi trang thai don, tien, duyet tai xe...). Giu actor,
--           hanh dong, loai/ma thuc the va chi tiet. Admin/Manager xem qua UI
--           "Nhat ky he thong".
-- Nguon migration: V22.
-- Constitution: HR-13 (audit log bat buoc), AC-07 (TIMESTAMPTZ). Khong xoa.
-- =============================================================================

CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID,
    actor_email VARCHAR(255),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(100),
    detail      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created_at  ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_action      ON audit_log (action);
CREATE INDEX idx_audit_log_entity_type ON audit_log (entity_type);

COMMENT ON TABLE audit_log IS 'Nhat ky audit append-only cho su kien bao mat/nghiep vu (HR-13). KHONG xoa.';


-- >>> Nguon: database/26_order_cancellation_refund.sql
-- =============================================================================
-- Bang: order_cancellation_refund  (26/32)
-- Tac dung: Yeu cau HOAN COC khi Khach chu dong huy don luc CHUA co tai xe nhan
--           (don o CONFIRMED, driver_id NULL). Khach nhap ly do + dinh kem anh;
--           Manager duyet thu cong -> hoan coc 30% ve customer_wallet, hoac tu
--           choi. Moi don toi da 1 yeu cau (order_id UNIQUE).
-- Nguon migration: V41.
-- Constitution: HR-14 (chinh sach hoan coc), AC-08 (NUMERIC(15,0)), AC-07,
--               AC-13 (refund ghi transaction o tang service), AC-14 (VARCHAR+CHECK).
-- Phu thuoc: service_order, app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE order_cancellation_refund (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id         UUID          NOT NULL REFERENCES service_order(id),
    customer_id      UUID          NOT NULL REFERENCES app_user(id),
    reason           VARCHAR(500)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'REFUNDED', 'REJECTED')),
    refund_amount    NUMERIC(15,0) CHECK (refund_amount IS NULL OR refund_amount >= 0),   -- = coc 30% khi REFUNDED
    rejection_reason VARCHAR(500),
    processed_by     UUID          REFERENCES app_user(id),
    processed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_cancellation_refund     PRIMARY KEY (id),
    CONSTRAINT uq_order_cancellation_refund_order UNIQUE (order_id),
    CONSTRAINT ck_order_cancellation_refund_terminal CHECK (
        (status = 'PENDING'  AND processed_by IS NULL     AND processed_at IS NULL
                             AND refund_amount IS NULL      AND rejection_reason IS NULL)
     OR (status = 'REFUNDED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                             AND refund_amount IS NOT NULL AND refund_amount > 0 AND rejection_reason IS NULL)
     OR (status = 'REJECTED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                             AND rejection_reason IS NOT NULL AND refund_amount IS NULL)
    )
);

CREATE INDEX idx_order_cancellation_refund_pending  ON order_cancellation_refund (created_at ASC, id ASC) WHERE status = 'PENDING';
CREATE INDEX idx_order_cancellation_refund_customer ON order_cancellation_refund (customer_id, created_at DESC, id DESC);

CREATE TRIGGER trg_order_cancellation_refund_updated_at
    BEFORE UPDATE ON order_cancellation_refund
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  order_cancellation_refund              IS 'Hoan coc khi khach huy don luc chua co tai xe (CONFIRMED). Manager duyet -> refund ve customer_wallet (HR-14).';
COMMENT ON COLUMN order_cancellation_refund.refund_amount IS 'So tien hoan (coc 30% = total_quote * commission_rate_snapshot floor) khi REFUNDED.';


-- >>> Nguon: database/27_order_cancellation_photo.sql
-- =============================================================================
-- Bang: order_cancellation_photo  (27/32)
-- Tac dung: Anh bang chung Khach dinh kem khi tao yeu cau hoan coc (toi da 3
--           anh/yeu cau, enforce o service). Luu Cloudinary signed upload;
--           public_id de ky signed URL khi Manager xem.
-- Nguon migration: V41.
-- Constitution: AC-10 (Cloudinary signed), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE order_cancellation_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    cancellation_id     UUID         NOT NULL REFERENCES order_cancellation_refund(id),
    url                 VARCHAR(500) NOT NULL,
    public_id           VARCHAR(255) NOT NULL,
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_cancellation_photo PRIMARY KEY (id)
);

CREATE INDEX idx_order_cancellation_photo_cancellation ON order_cancellation_photo (cancellation_id, uploaded_at);

COMMENT ON TABLE order_cancellation_photo IS 'Anh bang chung khi huy don; Cloudinary signed upload (AC-10).';


-- >>> Nguon: database/28_blog_post.sql
-- =============================================================================
-- Bang: blog_post  (28/32)
-- Tac dung: Bai dang BLOG CONG DONG (Community Wall) — Khach dang review + anh ve
--           dich vu, rating tuy chon. Guest xem feed (chi doc), Customer dang.
--           Kiem duyet qua status VISIBLE/HIDDEN; soft delete.
-- Nguon migration: V42.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-09 (soft delete), AC-14 (VARCHAR+CHECK),
--               AC-10 (anh Cloudinary), HR-21 (khong reserved word). Khong lien quan tien.
-- Phu thuoc: app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE blog_post (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    author_id  UUID        NOT NULL REFERENCES app_user(id),
    content    TEXT        NOT NULL,
    rating     SMALLINT    CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),   -- tuy chon
    status     VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,                       -- soft delete (AC-09)

    CONSTRAINT pk_blog_post PRIMARY KEY (id)
);

CREATE INDEX idx_blog_post_feed   ON blog_post (created_at DESC, id DESC) WHERE status = 'VISIBLE' AND deleted_at IS NULL;
CREATE INDEX idx_blog_post_author ON blog_post (author_id, created_at DESC);

CREATE TRIGGER trg_blog_post_updated_at
    BEFORE UPDATE ON blog_post
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE blog_post IS 'Bai dang cong dong (review + anh) cua Customer; Guest xem feed. Kiem duyet VISIBLE/HIDDEN.';


-- >>> Nguon: database/29_blog_post_photo.sql
-- =============================================================================
-- Bang: blog_post_photo  (29/32)
-- Tac dung: Anh dinh kem bai dang blog cong dong (toi da 3 anh/bai). Vi la noi
--           dung CONG KHAI nen luu Cloudinary type=upload (public delivery), dung
--           truc tiep lam <img src>; van la signed upload server-side (AC-10).
-- Nguon migration: V42.
-- Constitution: AC-10 (Cloudinary signed server-side), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE blog_post_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    post_id             UUID         NOT NULL REFERENCES blog_post(id),
    url                 VARCHAR(500) NOT NULL,        -- secure_url public
    public_id           VARCHAR(255) NOT NULL,        -- de xoa asset khi go bai
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_blog_post_photo PRIMARY KEY (id)
);

CREATE INDEX idx_blog_post_photo_post ON blog_post_photo (post_id, uploaded_at);

COMMENT ON TABLE blog_post_photo IS 'Anh dinh kem bai blog cong dong; Cloudinary signed upload, delivery public (AC-10).';


-- >>> Nguon: database/30_blog_comment.sql
-- =============================================================================
-- Bang: blog_comment  (30/32)
-- Tac dung: Binh luan duoi bai blog cong dong. Customer binh luan; Manager tra
--           loi (author_role de render badge "Quan ly" khong can join). Kiem duyet
--           VISIBLE/HIDDEN; soft delete.
-- Nguon migration: V43.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-09 (soft delete), AC-14 (VARCHAR+CHECK),
--               HR-21 (khong reserved word).
-- Phu thuoc: blog_post, app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE blog_comment (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id     UUID        NOT NULL REFERENCES blog_post(id),
    author_id   UUID        NOT NULL REFERENCES app_user(id),
    author_role VARCHAR(20) NOT NULL CHECK (author_role IN ('CUSTOMER', 'MANAGER')),   -- snapshot vai tro
    content     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT pk_blog_comment PRIMARY KEY (id)
);

CREATE INDEX idx_blog_comment_post ON blog_comment (post_id, created_at ASC, id ASC) WHERE status = 'VISIBLE' AND deleted_at IS NULL;

CREATE TRIGGER trg_blog_comment_updated_at
    BEFORE UPDATE ON blog_comment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE blog_comment IS 'Binh luan blog cong dong; Customer binh luan, Manager tra loi (author_role). Kiem duyet VISIBLE/HIDDEN.';


-- >>> Nguon: database/31_driver_incident_report.sql
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


-- >>> Nguon: database/32_driver_incident_photo.sql
-- =============================================================================
-- Bang: driver_incident_photo  (32/32)
-- Tac dung: Anh bang chung Tai xe dinh kem khi bao su co (toi da 3 anh/su co,
--           enforce o service). Luu Cloudinary signed upload; public_id de ky
--           signed URL khi Manager xem.
-- Nguon migration: V44.
-- Constitution: AC-10 (Cloudinary signed), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE driver_incident_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    incident_id         UUID         NOT NULL REFERENCES driver_incident_report(id),
    url                 VARCHAR(500) NOT NULL,
    public_id           VARCHAR(255) NOT NULL,
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_incident_photo PRIMARY KEY (id)
);

CREATE INDEX idx_driver_incident_photo_incident ON driver_incident_photo (incident_id, uploaded_at);

COMMENT ON TABLE driver_incident_photo IS 'Anh bang chung khi tai xe bao su co; Cloudinary signed upload (AC-10).';


COMMIT;
