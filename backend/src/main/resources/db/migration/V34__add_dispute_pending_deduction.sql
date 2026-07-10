-- =============================================================================
-- V34: Khấu trừ tài xế cho khiếu nại (nhánh RESOLVED_DEDUCT).
-- Luồng leader chốt 2026-07-10 (khác CONTEXT §2 thứ tự cọc-trước, khớp HR-18 ví-trước):
--   1. Manager khấu trừ → trừ VÍ tài xế ngay, hoàn phần đó cho khách.
--   2. Thiếu → lưu shortfall + deadline (2 phút, demo), thông báo tài xế nộp bổ sung.
--   3. Quá hạn chưa nộp → khóa tài khoản (SUSPENDED) + trừ CỌC bồi thường khách.
-- 2 cột nullable — NULL nghĩa là không có khoản khấu trừ nào đang chờ.
-- =============================================================================

ALTER TABLE dispute ADD COLUMN pending_deduct_shortfall NUMERIC(15,0);
ALTER TABLE dispute ADD COLUMN deduct_deadline TIMESTAMPTZ;

COMMENT ON COLUMN dispute.pending_deduct_shortfall IS 'Số tiền tài xế còn thiếu phải nộp bổ sung (VND). NULL = không có khoản chờ.';
COMMENT ON COLUMN dispute.deduct_deadline IS 'Hạn chót tài xế nộp bổ sung; quá hạn job sẽ khóa tài khoản và trừ cọc.';

-- Index cho scheduled job quét các khoản quá hạn
CREATE INDEX idx_dispute_deduct_deadline
    ON dispute (deduct_deadline)
    WHERE pending_deduct_shortfall IS NOT NULL;
