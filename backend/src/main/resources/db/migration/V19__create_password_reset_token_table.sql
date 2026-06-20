-- =============================================================================
-- V19: Tạo bảng token đặt lại mật khẩu còn thiếu sau Sprint 3.
-- Chỉ lưu SHA-256 hash của token; raw token chỉ được gửi cho người dùng.
-- AC-07: mọi thời điểm dùng TIMESTAMPTZ.
-- =============================================================================

CREATE TABLE password_reset_token (

    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID            NOT NULL
                REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)     NOT NULL,
    expires_at  TIMESTAMPTZ     NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);


CREATE INDEX idx_password_reset_token_user_active
    ON password_reset_token (user_id, expires_at)
    WHERE used_at IS NULL;


COMMENT ON TABLE password_reset_token IS 'Token đặt lại mật khẩu; chỉ lưu SHA-256 hash, không lưu raw token.';
COMMENT ON COLUMN password_reset_token.token_hash IS 'SHA-256 hash 64 ký tự của token đặt lại mật khẩu.';
COMMENT ON COLUMN password_reset_token.expires_at IS 'Thời điểm token hết hạn.';
COMMENT ON COLUMN password_reset_token.used_at IS 'NULL khi token chưa dùng; có giá trị sau khi đặt lại mật khẩu thành công.';
