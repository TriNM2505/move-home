-- =============================================================================
-- V1: Tao bang app_user — Nen tang xac thuc va phan quyen
-- Spec #001 Auth/RBAC v2.0, Constitution AC-07 (TIMESTAMPTZ), AC-09 (soft delete),
-- Constitution AC-12 (Flyway migration bat buoc).
--
-- Luu y ten bang: dung "app_user" thay vi "user" vi "user" la reserved word
-- trong PostgreSQL — dung "user" can phai quote trong moi query ("user").
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Ham tu dong cap nhat cot updated_at truoc moi UPDATE.
-- Dung chung cho nhieu bang, tao 1 lan o day.
-- IF NOT EXISTS de tranh loi neu ham da ton tai (an toan khi re-run).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    -- Cap nhat updated_at = thoi gian hien tai UTC (AC-07)
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- -----------------------------------------------------------------------------
-- Bang chinh luu thong tin nguoi dung: Customer, Driver, Manager, Admin.
-- 4 vai tro trong 1 bang (single-table) de don gian hoa JWT lookup.
-- Mot so cot chi co gia tri voi Driver (operating_districts, rejection_reason),
-- mot so chi co voi Customer (username) — NULL cho vai tro khac.
-- -----------------------------------------------------------------------------
CREATE TABLE app_user (

    -- Khoa chinh UUID — khong dung SERIAL/BIGINT de tranh lo thong tin thu tu tao (AC-09)
    id                   UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Phan quyen: 4 vai tro co dinh (HR-10). STRING enum tu Java UserRole
    role                 VARCHAR(20)     NOT NULL
                         CHECK (role IN ('CUSTOMER', 'DRIVER', 'MANAGER', 'ADMIN')),

    -- Trang thai tai khoan — 7 trang thai theo business flow (Spec #001 Data Model)
    -- PENDING_VERIFY: vua dang ky, chua verify email
    -- PENDING_DOCUMENTS: Driver da verify email, chua upload giay to (Buoc 2)
    -- PENDING_DEPOSIT: Driver da upload giay to, chua dong coc 3tr (Buoc 3)
    -- PENDING_APPROVAL: Driver da dong coc, cho Manager duyet (Buoc 4)
    -- ACTIVE: dang hoat dong binh thuong
    -- SUSPENDED: bi khoa (DamageReport tru het coc + vi, hoac Admin quyet dinh)
    -- REJECTED: Driver bi Manager tu choi, co the re-submit
    status               VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFY'
                         CHECK (status IN (
                             'ACTIVE',
                             'PENDING_VERIFY',
                             'PENDING_DOCUMENTS',
                             'PENDING_DEPOSIT',
                             'PENDING_APPROVAL',
                             'SUSPENDED',
                             'REJECTED'
                         )),

    -- Truong danh tinh — dung chung cho tat ca vai tro
    email                VARCHAR(255)    NOT NULL,
    phone                VARCHAR(20),                         -- Chuan hoa +84xxxxxxxxx truoc khi luu
    password_hash        VARCHAR(255)    NOT NULL,            -- BCrypt $2a$12$... (HR-02)
    full_name            VARCHAR(100)    NOT NULL,

    -- Username: chi dung cho CUSTOMER dang nhap; NULL cho Driver/Staff
    username             VARCHAR(30),

    -- Thong tin bo sung (co the NULL)
    date_of_birth        DATE,
    address              VARCHAR(500),

    -- Truong rieng cho DRIVER — NULL cho Customer va Staff
    -- PostgreSQL native array de luu nhieu quan hoat dong
    operating_districts  TEXT[],
    rejection_reason     TEXT,                                -- Ly do bi REJECTED (FR-067)

    -- Xac thuc email — true sau khi click link verify (FR-014 cho Customer, FR-047 cho Driver)
    email_verified       BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Dieu khoan su dung — Customer va Driver phai dong y khi dang ky (FR-002, FR-042)
    terms_accepted       BOOLEAN         NOT NULL DEFAULT FALSE,
    terms_accepted_at    TIMESTAMPTZ,

    -- Quan ly xac thuc
    must_change_password BOOLEAN         NOT NULL DEFAULT FALSE, -- Manager/Admin lan dau dang nhap (FR-036)
    failed_login_count   INTEGER         NOT NULL DEFAULT 0,     -- Dem sai password lien tiep (HR-16)
    last_failed_login_at TIMESTAMPTZ,                            -- Timestamp sai password cuoi (FR-034)
    locked_until         TIMESTAMPTZ,                            -- NULL = khong bi khoa (HR-16, FR-021)

    -- Audit timestamps — luu UTC (AC-07)
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Soft delete — AC-09. NULL = ban ghi con hieu luc; co gia tri = da xoa logic
    -- KHONG bao gio dung DELETE FROM app_user; chi UPDATE SET deleted_at = NOW()
    deleted_at           TIMESTAMPTZ,

    CONSTRAINT pk_app_user PRIMARY KEY (id)
);


-- -----------------------------------------------------------------------------
-- Constraints bo sung
-- -----------------------------------------------------------------------------

-- Email phai unique trong cac tai khoan CHUA bi xoa (partial unique index phia duoi)
-- Nhung doi voi ALL records (ke ca da xoa), email van phai unique trong he thong:
ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_email UNIQUE (email);

-- Username unique (chi Customer dung, nullable cho Driver/Staff)
ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_username UNIQUE (username);


-- -----------------------------------------------------------------------------
-- Indexes cho hieu nang query pho bien
-- Tat ca dung partial index WHERE deleted_at IS NULL (chi indexing ban ghi con hieu luc)
-- -----------------------------------------------------------------------------

-- Index tim kiem theo email (login va kiem tra trung) — quan trong nhat
CREATE INDEX idx_user_email_active
    ON app_user (email)
    WHERE deleted_at IS NULL;

-- Index ket hop role + status cho quan ly Driver, phan quyen, KPI dashboard
CREATE INDEX idx_user_role_status
    ON app_user (role, status)
    WHERE deleted_at IS NULL;

-- Index cho scheduled job kiem tra tai khoan bi khoa het han (FR-034)
CREATE INDEX idx_user_locked_until
    ON app_user (locked_until)
    WHERE locked_until IS NOT NULL;

-- Index cho lich su audit — tim kiem theo thoi gian tao (bao gom ban ghi da xoa)
CREATE INDEX idx_user_created_at
    ON app_user (created_at DESC);


-- -----------------------------------------------------------------------------
-- Trigger tu dong cap nhat updated_at moi khi ban ghi app_user thay doi.
-- Ham update_updated_at_column() da duoc tao o phia tren.
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- -----------------------------------------------------------------------------
-- Comment doc tai lieu cho cac cot quan trong (giup doc trong psql \d app_user)
-- -----------------------------------------------------------------------------
COMMENT ON TABLE  app_user                     IS 'Bang nguoi dung: Customer, Driver, Manager, Admin. Dung soft delete (deleted_at).';
COMMENT ON COLUMN app_user.role                IS 'Vai tro: CUSTOMER | DRIVER | MANAGER | ADMIN (HR-10)';
COMMENT ON COLUMN app_user.status              IS 'Trang thai tai khoan. Driver co nhieu trang thai onboarding hon Customer.';
COMMENT ON COLUMN app_user.password_hash       IS 'BCrypt cost 12. KHONG bao gio luu plaintext (HR-02).';
COMMENT ON COLUMN app_user.operating_districts IS 'Quan hoat dong cua Driver (1-12 quan noi thanh Ha Noi). NULL cho Customer/Staff.';
COMMENT ON COLUMN app_user.failed_login_count  IS 'Dem so lan sai password lien tiep. Reset ve 0 khi dang nhap thanh cong (HR-16).';
COMMENT ON COLUMN app_user.locked_until        IS 'Tai khoan bi khoa den thoi gian nay. NULL = khong bi khoa (HR-16, FR-021).';
COMMENT ON COLUMN app_user.deleted_at          IS 'Soft delete: NULL = con hieu luc, co gia tri = da xoa logic (AC-09). KHONG dung DELETE.';
