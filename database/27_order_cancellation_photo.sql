-- =============================================================================
-- Bang: order_cancellation_photo  (27/32)
-- Tac dung: Anh bang chung Khach dinh kem khi tao yeu cau hoan coc (toi da 3
--           anh/yeu cau, enforce o service). Luu Cloudinary signed upload;
--           public_id de ky signed URL khi Manager xem.
-- Nguon migration: V41.
-- Constitution: AC-10 (Cloudinary signed), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE order_cancellation_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    cancellation_id     UUID         NOT NULL REFERENCES order_cancellation_refund(id),
    url                 VARCHAR(500) NOT NULL,
    public_id           VARCHAR(255) NOT NULL,
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_cancellation_photo PRIMARY KEY (id)
);

CREATE INDEX idx_order_cancellation_photo_cancellation ON order_cancellation_photo (cancellation_id, uploaded_at);

COMMENT ON TABLE order_cancellation_photo IS 'Anh bang chung khi huy don; Cloudinary signed upload (AC-10).';
