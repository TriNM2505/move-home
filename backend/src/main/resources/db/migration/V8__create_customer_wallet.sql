-- =============================================================================
-- V8: Tạo bảng customer_wallet lưu số dư tổng hợp của khách hàng.
-- Sổ cái giao dịch tiền vẫn tái dùng bảng transaction đã tạo ở V6; không tạo
-- bảng giao dịch ví mới trong migration này.
-- AC-08: tiền dùng NUMERIC(15,0). AC-07: timestamp dùng TIMESTAMPTZ.
-- =============================================================================

CREATE TABLE customer_wallet (

    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Mỗi khách hàng có tối đa một ví tổng hợp.
    customer_id        UUID            NOT NULL UNIQUE
                       REFERENCES app_user(id),

    -- Số dư khả dụng của khách hàng, không được âm.
    balance            NUMERIC(15,0)   NOT NULL DEFAULT 0
                       CHECK (balance >= 0),

    -- Tổng tiền đã nạp và tổng tiền đã chi, dùng cho màn hình tổng quan ví.
    total_topped_up    NUMERIC(15,0)   NOT NULL DEFAULT 0,
    total_spent        NUMERIC(15,0)   NOT NULL DEFAULT 0,

    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_customer_wallet PRIMARY KEY (id)
);


-- Trigger tự động cập nhật updated_at, dùng lại hàm đã tạo ở V1.
CREATE TRIGGER trg_customer_wallet_updated_at
    BEFORE UPDATE ON customer_wallet
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMENT ON TABLE customer_wallet IS 'Ví tổng hợp của khách hàng. Giao dịch tiền chi tiết vẫn ghi append-only ở bảng transaction V6.';
COMMENT ON COLUMN customer_wallet.customer_id IS 'Khách hàng sở hữu ví; ràng buộc UNIQUE đảm bảo một ví cho một khách hàng.';
COMMENT ON COLUMN customer_wallet.balance IS 'Số dư khả dụng của khách hàng. NUMERIC(15,0) VND và không được âm.';
COMMENT ON COLUMN customer_wallet.total_topped_up IS 'Tổng tiền khách hàng đã nạp vào ví. NUMERIC(15,0) VND.';
COMMENT ON COLUMN customer_wallet.total_spent IS 'Tổng tiền khách hàng đã chi từ ví. NUMERIC(15,0) VND.';
COMMENT ON COLUMN customer_wallet.updated_at IS 'Thời điểm cập nhật ví gần nhất, tự động đổi qua trigger.';
