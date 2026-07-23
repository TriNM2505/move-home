-- =============================================================================
-- Bang: commission_settings_history  (21/32)
-- Tac dung: Lich su thay doi cau hinh gia/hoa hong (append-only). Moi lan Admin
--           cap nhat commission_settings ghi 1 snapshot: version cu/moi, nguoi
--           thay doi, gia tri cu/moi va diff (JSONB) — phuc vu audit va doi soat.
-- Nguon migration: V16.
-- Constitution: AC-07 (TIMESTAMPTZ). Spec #014. Append-only (khong UPDATE/DELETE).
-- =============================================================================

CREATE TABLE commission_settings_history (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    from_version BIGINT        NOT NULL,
    to_version   BIGINT        NOT NULL,
    changed_by   UUID          NOT NULL REFERENCES app_user(id),
    old_values   JSONB         NOT NULL,
    new_values   JSONB         NOT NULL,
    diff         JSONB         NOT NULL,
    note         VARCHAR(1000),
    changed_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_commission_settings_history       PRIMARY KEY (id),
    CONSTRAINT uq_settings_history_to_version        UNIQUE (to_version),
    CONSTRAINT ck_settings_history_version_progression CHECK (to_version > from_version)
);

CREATE INDEX idx_settings_history_changed_at ON commission_settings_history (changed_at DESC, id DESC);

COMMENT ON TABLE commission_settings_history IS 'Lich su snapshot cau hinh gia/hoa hong (append-only) — audit + doi soat.';
