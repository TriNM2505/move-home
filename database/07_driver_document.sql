-- =============================================================================
-- Bang: driver_document  (07/32)
-- Tac dung: Luu tai lieu onboarding cua Tai xe (GPLX truoc/sau, dang ky xe
--           truoc/sau, anh xe truoc/sau/ngang, anh chan dung). Anh upload qua
--           Cloudinary signed upload; public_id dung de ky signed URL TTL 1h.
-- Nguon migration: V14 (goc, 3 loai) + V29 (public_id) + V31/V32 (mo rong len 11 loai).
-- Constitution: AC-10 (Cloudinary signed), AC-14 (VARCHAR+CHECK), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE driver_document (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    driver_id   UUID         NOT NULL REFERENCES app_user(id),
    doc_type    VARCHAR(40)  NOT NULL
                CHECK (doc_type IN (
                    -- 8 loai chuan hien tai (V32)
                    'DRIVING_LICENSE_FRONT',
                    'DRIVING_LICENSE_BACK',
                    'VEHICLE_REGISTRATION_FRONT',
                    'VEHICLE_REGISTRATION_BACK',
                    'VEHICLE_PHOTO_FRONT',
                    'VEHICLE_PHOTO_REAR',
                    'VEHICLE_PHOTO_SIDE',
                    'FACE_PHOTO',
                    -- 3 loai cu (giu cho du lieu lich su truoc V32)
                    'DRIVING_LICENSE',
                    'VEHICLE_REGISTRATION',
                    'VEHICLE_PHOTO'
                )),
    url         VARCHAR(500) NOT NULL,
    public_id   VARCHAR(255),                 -- V29: Cloudinary public_id (ky signed URL); NULL cho tai lieu cu
    uploaded_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_document PRIMARY KEY (id)
);

CREATE INDEX idx_driver_document_driver_id ON driver_document (driver_id);

COMMENT ON TABLE  driver_document          IS 'Tai lieu onboarding tai xe (Cloudinary). 11 loai doc_type (8 chuan + 3 legacy).';
COMMENT ON COLUMN driver_document.public_id IS 'Cloudinary public_id, ky signed URL TTL 1h (spec 008). Nullable cho tai lieu cu.';
