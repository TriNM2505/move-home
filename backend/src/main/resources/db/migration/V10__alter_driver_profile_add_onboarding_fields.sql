-- =============================================================================
-- V10: Bổ sung các trường onboarding còn thiếu cho hồ sơ tài xế.
-- Spec #005: trạng thái vòng đời thuộc app_user.status; tài liệu và xe chi tiết
-- thuộc driver_document/driver_vehicle, không lưu trùng URL hoặc trạng thái tại đây.
-- AC-07: thời gian dùng TIMESTAMPTZ.
-- =============================================================================

ALTER TABLE driver_profile
    ADD COLUMN IF NOT EXISTS license_expiry_date DATE,
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS profile_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE driver_profile
    ADD CONSTRAINT ck_driver_profile_license_class
    CHECK (license_class IS NULL OR license_class IN ('B1', 'B2', 'C', 'D'));


COMMENT ON COLUMN driver_profile.license_expiry_date IS 'Ngày hết hạn giấy phép lái xe của tài xế.';
COMMENT ON COLUMN driver_profile.onboarding_completed_at IS 'Thời điểm tài xế hoàn tất onboarding và được duyệt.';
COMMENT ON COLUMN driver_profile.last_reminder_at IS 'Thời điểm gửi email nhắc onboarding gần nhất.';
COMMENT ON COLUMN driver_profile.profile_version IS 'Phiên bản hồ sơ dùng cho kiểm soát cập nhật đồng thời.';
