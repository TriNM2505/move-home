-- V29__add_public_id_to_driver_document.sql
-- Spec 008: signed URL TTL 1h cho tai lieu tai xe.
-- Cot nullable vi tai lieu cu (truoc khi co Cloudinary that) chua co public_id.
-- Khi generate signed URL: public_id IS NULL → fallback tra url tho.
ALTER TABLE driver_document
ADD COLUMN IF NOT EXISTS public_id VARCHAR(255);

COMMENT ON COLUMN driver_document.public_id IS 'Cloudinary public_id, dung de generate signed URL TTL 1h (spec 008). Nullable cho tai lieu cu.';