-- =============================================================================
-- V32: Mo rong loai giay to tai xe len 8 loai de xac thuc ky hon.
-- 8 loai bat buoc: GPLX truoc/sau, dang ky xe truoc/sau, anh xe truoc/sau/ngang, anh chan dung.
-- Giu 3 loai cu (DRIVING_LICENSE, VEHICLE_REGISTRATION, VEHICLE_PHOTO) trong CHECK
-- de khong vi pham rang buoc voi cac hang du lieu tai xe da co truoc day.
-- =============================================================================

ALTER TABLE driver_document
    DROP CONSTRAINT IF EXISTS driver_document_doc_type_check;

ALTER TABLE driver_document
    ADD CONSTRAINT driver_document_doc_type_check
    CHECK (doc_type IN (
        -- 8 loai moi
        'DRIVING_LICENSE_FRONT',
        'DRIVING_LICENSE_BACK',
        'VEHICLE_REGISTRATION_FRONT',
        'VEHICLE_REGISTRATION_BACK',
        'VEHICLE_PHOTO_FRONT',
        'VEHICLE_PHOTO_REAR',
        'VEHICLE_PHOTO_SIDE',
        'FACE_PHOTO',
        -- loai cu (giu cho tai xe da duyet truoc day)
        'DRIVING_LICENSE',
        'VEHICLE_REGISTRATION',
        'VEHICLE_PHOTO'
    ));

COMMENT ON COLUMN driver_document.doc_type IS 'Loai tai lieu (8 loai): GPLX truoc/sau, dang ky xe truoc/sau, anh xe truoc/sau/ngang, anh chan dung. Con giu 3 loai cu cho du lieu lich su.';
