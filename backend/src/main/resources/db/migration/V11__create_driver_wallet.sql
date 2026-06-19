-- =============================================================================
-- V11: Tạo ví tổng hợp cho tài xế.
-- Sổ cái chi tiết tiếp tục dùng bảng transaction append-only đã tạo ở V6.
-- AC-08: tiền dùng NUMERIC(15,0). AC-07: thời gian dùng TIMESTAMPTZ.
-- =============================================================================

CREATE TABLE driver_wallet (

    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Mỗi tài xế có tối đa một ví tổng hợp.
    driver_id          UUID            NOT NULL UNIQUE
                       REFERENCES app_user(id),

    -- Số dư khả dụng không được âm.
    balance            NUMERIC(15,0)   NOT NULL DEFAULT 0
                       CHECK (balance >= 0),

    -- Các số liệu tổng hợp phục vụ đối soát và màn hình thu nhập.
    total_earned       NUMERIC(15,0)   NOT NULL DEFAULT 0
                       CHECK (total_earned >= 0),
    total_withdrawn    NUMERIC(15,0)   NOT NULL DEFAULT 0
                       CHECK (total_withdrawn >= 0),

    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_wallet PRIMARY KEY (id)
);


-- Tự động cập nhật updated_at, dùng lại hàm đã tạo ở V1.
CREATE TRIGGER trg_driver_wallet_updated_at
    BEFORE UPDATE ON driver_wallet
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMENT ON TABLE driver_wallet IS 'Ví tổng hợp của tài xế; giao dịch tiền chi tiết ghi append-only trong bảng transaction.';
COMMENT ON COLUMN driver_wallet.driver_id IS 'Tài xế sở hữu ví; UNIQUE bảo đảm mỗi tài xế chỉ có một ví.';
COMMENT ON COLUMN driver_wallet.balance IS 'Số dư khả dụng của tài xế, NUMERIC(15,0) VND và không âm.';
COMMENT ON COLUMN driver_wallet.total_earned IS 'Tổng thu nhập đã ghi nhận của tài xế, NUMERIC(15,0) VND.';
COMMENT ON COLUMN driver_wallet.total_withdrawn IS 'Tổng tiền tài xế đã rút, NUMERIC(15,0) VND.';
COMMENT ON COLUMN driver_wallet.updated_at IS 'Thời điểm cập nhật ví gần nhất, tự động đổi qua trigger.';
