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
