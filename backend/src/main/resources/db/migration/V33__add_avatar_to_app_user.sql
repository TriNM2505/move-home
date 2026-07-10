-- =============================================================================
-- V33: Thêm cột avatar cho app_user — ảnh đại diện upload qua Cloudinary (AC-10).
-- avatar_url: secure_url Cloudinary để hiển thị trực tiếp (public delivery).
-- avatar_public_id: dùng để destroy ảnh cũ trên Cloudinary khi user thay avatar
--                   (tránh rác free tier theo AC-10 cleanup).
-- Cả 2 cột nullable — user chưa upload avatar thì NULL, không ảnh hưởng data cũ.
-- =============================================================================

ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(500);
ALTER TABLE app_user ADD COLUMN avatar_public_id VARCHAR(255);

COMMENT ON COLUMN app_user.avatar_url IS 'URL ảnh đại diện trên Cloudinary (secure_url, public delivery). NULL nếu chưa upload.';
COMMENT ON COLUMN app_user.avatar_public_id IS 'Cloudinary public_id của avatar — dùng để xóa ảnh cũ khi thay avatar mới.';
