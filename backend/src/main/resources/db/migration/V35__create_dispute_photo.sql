-- =============================================================================
-- V35: Ảnh bằng chứng khách đính kèm khi tạo khiếu nại (DamageReport evidence).
-- AC-10: lưu Cloudinary (signed upload, không lưu BLOB), tối đa 3 ảnh/khiếu nại.
-- Tên bảng dispute_photo khớp thực thể dispute đang dùng (không phải damage_report).
-- AC-07: thời gian TIMESTAMPTZ. HR-21: không dùng reserved word.
-- =============================================================================

CREATE TABLE dispute_photo (

    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),

    dispute_id            UUID            NOT NULL
                          REFERENCES dispute(id),

    -- secure_url Cloudinary (resource_type=authenticated); hiển thị qua signed URL mới tạo.
    url                   VARCHAR(500)    NOT NULL,
    -- public_id để ký URL khi manager xem + để xóa asset khi cần.
    public_id             VARCHAR(255)    NOT NULL,

    uploaded_by_user_id   UUID            REFERENCES app_user(id),
    uploaded_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_photo PRIMARY KEY (id)
);

CREATE INDEX idx_dispute_photo_dispute
    ON dispute_photo (dispute_id, uploaded_at);

COMMENT ON TABLE dispute_photo IS 'Ảnh bằng chứng khách đính kèm khiếu nại; lưu Cloudinary signed upload theo AC-10.';
COMMENT ON COLUMN dispute_photo.public_id IS 'Cloudinary public_id (resource_type=authenticated) để ký URL khi xem.';
