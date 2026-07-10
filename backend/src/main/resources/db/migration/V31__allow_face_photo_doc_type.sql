-- =============================================================================
-- V31: Cho phep them loai giay to FACE_PHOTO (anh chan dung tai xe).
-- V14 tao CHECK constraint doc_type chi voi 3 loai; noi rong de them FACE_PHOTO
-- phuc vu xac thuc danh tinh (manager doi chieu chan dung voi GPLX; khach xem khi nhan don).
-- Constraint inline cua V14 duoc PostgreSQL dat ten mac dinh driver_document_doc_type_check.
-- =============================================================================

ALTER TABLE driver_document
    DROP CONSTRAINT IF EXISTS driver_document_doc_type_check;

ALTER TABLE driver_document
    ADD CONSTRAINT driver_document_doc_type_check
    CHECK (doc_type IN (
        'DRIVING_LICENSE',
        'VEHICLE_REGISTRATION',
        'VEHICLE_PHOTO',
        'FACE_PHOTO'
    ));

COMMENT ON COLUMN driver_document.doc_type IS 'Loai tai lieu: DRIVING_LICENSE, VEHICLE_REGISTRATION, VEHICLE_PHOTO hoac FACE_PHOTO.';
