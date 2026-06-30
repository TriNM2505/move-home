-- =============================================================================
-- V26: Login events for canonical customer activity reports
-- =============================================================================

CREATE TABLE login_event (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    logged_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_login_event PRIMARY KEY (id),
    CONSTRAINT fk_login_event_user
        FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_login_event_logged_in_at_user
    ON login_event (logged_in_at, user_id);

CREATE INDEX idx_login_event_user_logged_in_at
    ON login_event (user_id, logged_in_at);

