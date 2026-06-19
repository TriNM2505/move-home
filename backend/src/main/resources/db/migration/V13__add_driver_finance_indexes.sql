-- =============================================================================
-- V13: Ràng buộc/index cho tài chính tài xế.
-- Không tạo lại bảng đã có; chỉ bổ sung idempotency và truy vấn nhanh.
-- AC-13: transaction vẫn là sổ cái chỉ-thêm.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_driver_earning_order
    ON transaction (related_order_id)
    WHERE type = 'DRIVER_EARNING'
      AND related_order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_driver_earning_created
    ON transaction (user_id, created_at DESC, id DESC)
    WHERE type = 'DRIVER_EARNING';

CREATE INDEX IF NOT EXISTS idx_withdrawal_driver_status_requested
    ON withdrawal_request (driver_id, status, requested_at DESC);
