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
