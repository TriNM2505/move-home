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
