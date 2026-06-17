-- =============================================================================
-- V9: Tạo bảng order_rating lưu đánh giá của khách hàng sau khi hoàn thành đơn.
-- Mỗi service_order chỉ có một đánh giá để tránh trùng phản hồi.
-- AC-14: thang sao dùng INTEGER + CHECK. AC-07: created_at dùng TIMESTAMPTZ.
-- =============================================================================

CREATE TABLE order_rating (

    id             UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Một đơn hàng chỉ được đánh giá một lần.
    order_id       UUID            NOT NULL UNIQUE
                   REFERENCES service_order(id),

    customer_id    UUID            NOT NULL
                   REFERENCES app_user(id),

    -- Có thể NULL nếu đơn cũ chưa gán tài xế hoặc dữ liệu legacy chưa đủ.
    driver_id      UUID
                   REFERENCES app_user(id),

    stars          INTEGER         NOT NULL
                   CHECK (stars BETWEEN 1 AND 5),

    comment        TEXT,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_rating PRIMARY KEY (id)
);


-- Index phục vụ tính rating trung bình của tài xế.
CREATE INDEX idx_order_rating_driver
    ON order_rating (driver_id);


COMMENT ON TABLE order_rating IS 'Đánh giá của khách hàng cho đơn chuyển nhà đã hoàn thành.';
COMMENT ON COLUMN order_rating.order_id IS 'Đơn hàng được đánh giá; UNIQUE để đảm bảo một đánh giá cho mỗi đơn.';
COMMENT ON COLUMN order_rating.customer_id IS 'Khách hàng tạo đánh giá.';
COMMENT ON COLUMN order_rating.driver_id IS 'Tài xế được đánh giá; có thể NULL cho dữ liệu cũ hoặc đơn chưa gán tài xế.';
COMMENT ON COLUMN order_rating.stars IS 'Số sao đánh giá từ 1 đến 5.';
COMMENT ON COLUMN order_rating.comment IS 'Nhận xét tự do của khách hàng.';
COMMENT ON COLUMN order_rating.created_at IS 'Thời điểm tạo đánh giá, lưu TIMESTAMPTZ theo AC-07.';
