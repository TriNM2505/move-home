-- =============================================================================
-- V14: Tạo bảng lưu tài liệu onboarding của tài xế.
-- Spec #005 chỉ cho phép: DRIVING_LICENSE, VEHICLE_REGISTRATION, VEHICLE_PHOTO.
-- AC-07: thời gian dùng TIMESTAMPTZ. HR-21: tên bảng không dùng reserved word.
-- =============================================================================

CREATE TABLE driver_document (

    id             UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Tài xế sở hữu tài liệu; cùng kiểu FK app_user(id) như V8.
    driver_id      UUID            NOT NULL
                   REFERENCES app_user(id),

    doc_type       VARCHAR(40)     NOT NULL
                   CHECK (doc_type IN (
                       'DRIVING_LICENSE',
                       'VEHICLE_REGISTRATION',
                       'VEHICLE_PHOTO'
                   )),

    url            VARCHAR(500)    NOT NULL,
    uploaded_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_document PRIMARY KEY (id)
);


CREATE INDEX idx_driver_document_driver_id
    ON driver_document (driver_id);


COMMENT ON TABLE driver_document IS 'Tài liệu onboarding do tài xế tải lên theo Spec #005.';
COMMENT ON COLUMN driver_document.doc_type IS 'Loại tài liệu: DRIVING_LICENSE, VEHICLE_REGISTRATION hoặc VEHICLE_PHOTO.';
COMMENT ON COLUMN driver_document.url IS 'URL tài liệu đã tải lên.';
COMMENT ON COLUMN driver_document.uploaded_at IS 'Thời điểm tải tài liệu lên, lưu bằng TIMESTAMPTZ.';
