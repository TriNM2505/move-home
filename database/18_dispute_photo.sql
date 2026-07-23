-- =============================================================================
-- Bang: dispute_photo  (18/32)
-- Tac dung: Anh bang chung Khach dinh kem KHI TAO khieu nai (toi da 3 anh/khieu
--           nai, enforce o service). Luu Cloudinary signed upload (resource_type
--           authenticated); public_id de ky signed URL khi Manager xem.
-- Nguon migration: V35.
-- Constitution: AC-10 (Cloudinary signed, khong luu BLOB), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE dispute_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    dispute_id          UUID         NOT NULL REFERENCES dispute(id),
    url                 VARCHAR(500) NOT NULL,        -- secure_url Cloudinary
    public_id           VARCHAR(255) NOT NULL,        -- de ky signed URL + xoa asset
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_photo PRIMARY KEY (id)
);

CREATE INDEX idx_dispute_photo_dispute ON dispute_photo (dispute_id, uploaded_at);

COMMENT ON TABLE dispute_photo IS 'Anh bang chung khach dinh kem khieu nai; Cloudinary signed upload (AC-10).';
