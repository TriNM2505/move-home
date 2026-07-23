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
