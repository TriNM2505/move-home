-- =============================================================================
-- V17: Tạo bảng lưu vị trí mới nhất của tài xế.
-- Spec #003 Customer Orders và Spec #006 Driver Workflow.
-- Mỗi tài xế có tối đa một bản ghi; cập nhật vị trí bằng UPSERT theo driver_id.
-- Lịch sử GPS chi tiết không thuộc phạm vi.
-- AC-07: mọi thời điểm dùng TIMESTAMPTZ.
-- =============================================================================

CREATE TABLE driver_location (

    driver_id         UUID            NOT NULL
                      REFERENCES app_user(id),
    current_order_id  UUID
                      REFERENCES service_order(id),
    lat               NUMERIC(10,7)   NOT NULL
                      CHECK (lat BETWEEN -90 AND 90),
    lng               NUMERIC(10,7)   NOT NULL
                      CHECK (lng BETWEEN -180 AND 180),
    heading           NUMERIC(5,2)
                      CHECK (heading IS NULL OR (heading >= 0 AND heading < 360)),
    speed_kmh         NUMERIC(6,2)
                      CHECK (speed_kmh IS NULL OR (speed_kmh >= 0 AND speed_kmh <= 180)),
    recorded_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_location PRIMARY KEY (driver_id)
);


CREATE INDEX idx_driver_location_current_order
    ON driver_location (current_order_id)
    WHERE current_order_id IS NOT NULL;


CREATE TRIGGER trg_driver_location_updated_at
    BEFORE UPDATE ON driver_location
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMENT ON TABLE driver_location IS 'Vị trí mới nhất của mỗi tài xế; cập nhật bằng UPSERT theo driver_id, không lưu lịch sử GPS.';
COMMENT ON COLUMN driver_location.driver_id IS 'Tài xế gửi vị trí; đồng thời là khóa chính để bảo đảm một snapshot mỗi tài xế.';
COMMENT ON COLUMN driver_location.current_order_id IS 'Đơn đang hoạt động của tài xế; NULL khi tài xế online nhưng chưa thực hiện đơn.';
COMMENT ON COLUMN driver_location.lat IS 'Vĩ độ mới nhất, NUMERIC(10,7), giới hạn từ -90 đến 90.';
COMMENT ON COLUMN driver_location.lng IS 'Kinh độ mới nhất, NUMERIC(10,7), giới hạn từ -180 đến 180.';
COMMENT ON COLUMN driver_location.heading IS 'Hướng di chuyển theo độ, từ 0 đến nhỏ hơn 360; có thể NULL.';
COMMENT ON COLUMN driver_location.speed_kmh IS 'Tốc độ mới nhất theo km/h, từ 0 đến 180; có thể NULL.';
COMMENT ON COLUMN driver_location.recorded_at IS 'Thời điểm nhận vị trí từ tài xế; dùng xác định dữ liệu stale.';
COMMENT ON COLUMN driver_location.updated_at IS 'Thời điểm bản ghi snapshot được cập nhật gần nhất.';
