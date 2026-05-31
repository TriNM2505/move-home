-- =============================================================================
-- V3: Tao bang refresh_token
-- Spec #001 FR-025 (phat hanh), FR-028 (rotation), FR-029 (reuse detection/PANIC).
-- Constitution AC-03 (JWT 7 ngay refresh + rotation + DB store).
-- Luu SHA-256 hash — KHONG luu raw token (HR-02 pattern cho token).
-- =============================================================================

CREATE TABLE refresh_token (

    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- FK toi app_user — cascade delete: user bi xoa → token tu dong xoa
    user_id              UUID        NOT NULL
                         REFERENCES app_user(id) ON DELETE CASCADE,

    -- SHA-256 hex cua raw refresh token (luon 64 ky tu — CHAR(64) du nhung VARCHAR linh hoat hon)
    token_hash           VARCHAR(64) NOT NULL,

    -- Token het han sau 7 ngay ke tu khi tao (AC-03)
    expires_at           TIMESTAMPTZ NOT NULL,

    -- NULL = token con hieu luc; co gia tri = da bi revoke (logout hoac rotation)
    revoked_at           TIMESTAMPTZ,

    -- UUID cua token MOI thay the sau rotation.
    -- Neu token da revoked ma co replacedByTokenId → ke tan cong dang reuse token cu (FR-029)
    -- Self-referential FK cho phep chain: token_1 → token_2 → token_3 ...
    replaced_by_token_id UUID
                         REFERENCES refresh_token(id) ON DELETE SET NULL,

    -- Thong tin thiet bi (ghi cho admin xem active sessions)
    user_agent           VARCHAR(500),

    -- IPv4 hoac IPv6 (IPv6 toi da 45 ky tu: 8 nhom x 4 hex + 7 dau cach)
    ip_address           VARCHAR(45),

    -- Thoi gian tao — KHONG co updated_at vi token immutable sau khi revoke
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_token          PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash     UNIQUE (token_hash)
);


-- Index tim token hash (query chinh: findByTokenHash)
CREATE INDEX idx_rt_token_hash
    ON refresh_token (token_hash);

-- Index tim token hieu luc cua mot user (findAllByUserIdAndRevokedAtIsNull + revokeAllByUserId)
CREATE INDEX idx_rt_user_active
    ON refresh_token (user_id)
    WHERE revoked_at IS NULL;

-- Index ho tro cleanup job xoa token het han > 30 ngay (NFR-06, Round 3 scheduled job)
CREATE INDEX idx_rt_expires_at
    ON refresh_token (expires_at)
    WHERE revoked_at IS NOT NULL;


COMMENT ON TABLE  refresh_token                       IS 'Refresh token luu server-side (hash). Rotation moi lan dung, revoke khi logout (AC-03).';
COMMENT ON COLUMN refresh_token.token_hash            IS 'SHA-256 hex cua raw token. Client giu raw token, server giu hash.';
COMMENT ON COLUMN refresh_token.replaced_by_token_id IS 'Token moi thay the sau rotation. Dung detect reuse attack (FR-029).';
COMMENT ON COLUMN refresh_token.revoked_at            IS 'NULL = con hieu luc. Set khi logout hoac rotation.';
