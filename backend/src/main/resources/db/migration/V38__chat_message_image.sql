-- ============================================================
-- V38: Chat gui anh (1 anh/tin nhan).
-- image_public_id = Cloudinary public_id (resource type authenticated) — hien thi qua SIGNED URL (AC-10).
-- NULL = tin nhan chi co text. Tin nhan anh: content = '' + image_public_id co gia tri.
-- ============================================================

ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS image_public_id TEXT;
