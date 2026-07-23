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
