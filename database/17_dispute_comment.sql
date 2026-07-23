-- =============================================================================
-- Bang: dispute_comment  (17/32)
-- Tac dung: Binh luan noi bo cua Manager/Admin trong qua trinh xu ly 1 khieu nai
--           (nhat ky trao doi). Co idempotency_key chong tao trung khi retry.
-- Nguon migration: V16.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK).
-- =============================================================================

CREATE TABLE dispute_comment (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    dispute_id      UUID        NOT NULL REFERENCES dispute(id),
    author_id       UUID        NOT NULL REFERENCES app_user(id),
    author_role     VARCHAR(20) NOT NULL CHECK (author_role IN ('MANAGER', 'ADMIN')),
    comment         TEXT        NOT NULL,
    idempotency_key UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_comment PRIMARY KEY (id),
    CONSTRAINT uq_dispute_comment_idempotency UNIQUE (author_id, idempotency_key)
);

CREATE INDEX idx_dispute_comment_timeline ON dispute_comment (dispute_id, created_at ASC, id ASC);

COMMENT ON TABLE dispute_comment IS 'Binh luan noi bo Manager/Admin khi xu ly khieu nai.';
