-- =============================================================================
-- Bang: dispute_evidence  (16/32)
-- Tac dung: Bang chung (anh/tai lieu) dinh kem 1 khieu nai, do cac ben upload
--           (Customer/Driver/Manager/Admin). Luu tren Cloudinary (public_id) +
--           metadata (content_type, kich thuoc). Dung dung timeline cua dispute.
-- Nguon migration: V16.
-- Constitution: AC-10 (Cloudinary), AC-07 (TIMESTAMPTZ), AC-14 (VARCHAR+CHECK).
-- =============================================================================

CREATE TABLE dispute_evidence (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    dispute_id           UUID         NOT NULL REFERENCES dispute(id),
    uploader_id          UUID         NOT NULL REFERENCES app_user(id),
    uploader_role        VARCHAR(20)  NOT NULL CHECK (uploader_role IN ('CUSTOMER', 'DRIVER', 'MANAGER', 'ADMIN')),
    evidence_type        VARCHAR(30)  NOT NULL CHECK (evidence_type IN ('PHOTO', 'DOCUMENT', 'OTHER')),
    cloudinary_public_id TEXT         NOT NULL,
    content_type         VARCHAR(100) NOT NULL,
    file_size_bytes      BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_evidence PRIMARY KEY (id)
);

CREATE INDEX idx_dispute_evidence_timeline ON dispute_evidence (dispute_id, created_at ASC, id ASC);

COMMENT ON TABLE dispute_evidence IS 'Bang chung khieu nai (Cloudinary) do cac ben upload theo timeline.';
