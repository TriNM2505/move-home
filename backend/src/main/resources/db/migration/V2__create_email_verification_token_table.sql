-- =============================================================================
-- V2: Tao bang email_verification_token
-- Spec #001 FR-008 (Customer register), FR-045 (Driver register), FR-011..FR-016 (verify flow).
-- Luu SHA-256 hash cua raw token — KHONG luu raw token (chong lo token qua DB breach).
-- =============================================================================

CREATE TABLE email_verification_token (

    id         UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- FK toi app_user — cascade delete: user bi xoa → token tu dong xoa
    user_id    UUID        NOT NULL
               REFERENCES app_user(id) ON DELETE CASCADE,

    -- SHA-256 hex cua raw token gui trong email link (64 ky tu, du cho VARCHAR(100))
    token      VARCHAR(100) NOT NULL,

    -- Token het han sau 24 gio ke tu khi tao (FR-008)
    expires_at TIMESTAMPTZ NOT NULL,

    -- Dau cham token da duoc su dung (NULL = chua dung, co gia tri = da verify)
    -- Chong reuse token (khi user click link lan 2)
    used_at    TIMESTAMPTZ,

    -- Thoi gian tao — dung cho bao cao va debug
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_verification_token PRIMARY KEY (id),
    CONSTRAINT uq_evtoken_token            UNIQUE (token)
);


-- Index tim token (query chinh: findByToken)
CREATE INDEX idx_evtoken_token
    ON email_verification_token (token);

-- Index tim theo user_id va token chua dung (deleteByUserId + phat hien duplicate)
CREATE INDEX idx_evtoken_user_active
    ON email_verification_token (user_id, expires_at)
    WHERE used_at IS NULL;


COMMENT ON TABLE  email_verification_token         IS 'Token xac thuc email (hash, het han 24h). Xoa khi tao token moi (FR-008).';
COMMENT ON COLUMN email_verification_token.token   IS 'SHA-256 hex cua raw token gui trong email. KHONG phai raw token.';
COMMENT ON COLUMN email_verification_token.used_at IS 'NULL = chua verify; co gia tri = da xac thuc email thanh cong.';
