-- =============================================================================
-- V40: Tài xế mặc định 5.00 sao khi CHƯA có đánh giá nào (quyết định leader 2026-07-11).
-- - Đổi DEFAULT cột average_rating từ 0.00 → 5.00 (tài xế mới đăng ký hiện 5 sao).
-- - Cập nhật tài xế hiện hữu chưa có dòng nào trong order_rating về 5.00.
-- Tài xế đã có đánh giá giữ nguyên trung bình thực (phương án (a) — trung bình thuần).
-- AC-08: giữ NUMERIC(3,2), không đổi kiểu cột.
-- =============================================================================

ALTER TABLE driver_profile
    ALTER COLUMN average_rating SET DEFAULT 5.00;

UPDATE driver_profile dp
SET average_rating = 5.00
WHERE NOT EXISTS (
    SELECT 1
    FROM order_rating r
    WHERE r.driver_id = dp.user_id
);

COMMENT ON COLUMN driver_profile.average_rating IS
    'Trung bình sao từ order_rating; mặc định 5.00 khi tài xế chưa có đánh giá nào (V40).';
