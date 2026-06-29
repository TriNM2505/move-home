-- =============================================================================
-- V27: User suspension source-of-truth fields for Admin suspend/reactivate
-- =============================================================================

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS suspension_previous_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspended_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS suspension_reason TEXT,
    ADD COLUMN IF NOT EXISTS suspension_until TIMESTAMPTZ;

ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS ck_app_user_suspension_fields;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_suspension_fields
    CHECK (
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
    );

