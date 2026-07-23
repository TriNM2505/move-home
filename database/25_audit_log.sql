-- =============================================================================
-- Bang: audit_log  (25/32)
-- Tac dung: Nhat ky he thong (audit) append-only cho cac su kien bao mat + nghiep
--           vu quan trong (doi trang thai don, tien, duyet tai xe...). Giu actor,
--           hanh dong, loai/ma thuc the va chi tiet. Admin/Manager xem qua UI
--           "Nhat ky he thong".
-- Nguon migration: V22.
-- Constitution: HR-13 (audit log bat buoc), AC-07 (TIMESTAMPTZ). Khong xoa.
-- =============================================================================

CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID,
    actor_email VARCHAR(255),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(100),
    detail      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created_at  ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_action      ON audit_log (action);
CREATE INDEX idx_audit_log_entity_type ON audit_log (entity_type);

COMMENT ON TABLE audit_log IS 'Nhat ky audit append-only cho su kien bao mat/nghiep vu (HR-13). KHONG xoa.';
