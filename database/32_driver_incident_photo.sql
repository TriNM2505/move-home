-- =============================================================================
-- Bang: driver_incident_photo  (32/32)
-- Tac dung: Anh bang chung Tai xe dinh kem khi bao su co (toi da 3 anh/su co,
--           enforce o service). Luu Cloudinary signed upload; public_id de ky
--           signed URL khi Manager xem.
-- Nguon migration: V44.
-- Constitution: AC-10 (Cloudinary signed), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE driver_incident_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    incident_id         UUID         NOT NULL REFERENCES driver_incident_report(id),
    url                 VARCHAR(500) NOT NULL,
    public_id           VARCHAR(255) NOT NULL,
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_incident_photo PRIMARY KEY (id)
);

CREATE INDEX idx_driver_incident_photo_incident ON driver_incident_photo (incident_id, uploaded_at);

COMMENT ON TABLE driver_incident_photo IS 'Anh bang chung khi tai xe bao su co; Cloudinary signed upload (AC-10).';
