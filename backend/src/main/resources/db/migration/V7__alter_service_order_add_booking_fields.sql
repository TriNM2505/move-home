-- =============================================================================
-- V7: Bổ sung dữ liệu đặt xe và breakdown giá cho service_order.
-- Bảng service_order đã được tạo ở V5, migration này chỉ ALTER ADD COLUMN.
-- Các cột mới đều nullable hoặc có DEFAULT để dữ liệu seed/legacy không bị vỡ.
-- AC-08: các khoản tiền dùng NUMERIC(15,0). AC-14: enum dùng VARCHAR + CHECK.
-- =============================================================================

ALTER TABLE service_order
    ADD COLUMN vehicle_type VARCHAR(20) NOT NULL DEFAULT 'TRUCK_500KG'
        CONSTRAINT ck_service_order_vehicle_type
        CHECK (vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')),
    ADD COLUMN porter_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN pickup_lat NUMERIC(10,7),
    ADD COLUMN pickup_lng NUMERIC(10,7),
    ADD COLUMN dropoff_lat NUMERIC(10,7),
    ADD COLUMN dropoff_lng NUMERIC(10,7),
    ADD COLUMN pickup_floor INTEGER,
    ADD COLUMN pickup_has_elevator BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pickup_has_alley BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dropoff_floor INTEGER,
    ADD COLUMN dropoff_has_elevator BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dropoff_has_alley BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN base_fare NUMERIC(15,0),
    ADD COLUMN peak_surcharge NUMERIC(15,0) DEFAULT 0,
    ADD COLUMN alley_surcharge NUMERIC(15,0) DEFAULT 0,
    ADD COLUMN floor_surcharge NUMERIC(15,0) DEFAULT 0,
    ADD COLUMN porter_fee NUMERIC(15,0) DEFAULT 0;


COMMENT ON COLUMN service_order.vehicle_type IS 'Loại xe khách chọn khi đặt đơn. Dùng VARCHAR + CHECK thay vì PostgreSQL enum.';
COMMENT ON COLUMN service_order.porter_count IS 'Số người bốc xếp khách yêu cầu cho đơn chuyển nhà.';
COMMENT ON COLUMN service_order.pickup_lat IS 'Vĩ độ điểm đón sau geocoding, lưu tối đa 7 chữ số thập phân.';
COMMENT ON COLUMN service_order.pickup_lng IS 'Kinh độ điểm đón sau geocoding, lưu tối đa 7 chữ số thập phân.';
COMMENT ON COLUMN service_order.dropoff_lat IS 'Vĩ độ điểm trả sau geocoding, lưu tối đa 7 chữ số thập phân.';
COMMENT ON COLUMN service_order.dropoff_lng IS 'Kinh độ điểm trả sau geocoding, lưu tối đa 7 chữ số thập phân.';
COMMENT ON COLUMN service_order.pickup_floor IS 'Tầng tại điểm đón; NULL cho dữ liệu cũ hoặc khi khách không cung cấp.';
COMMENT ON COLUMN service_order.pickup_has_elevator IS 'Điểm đón có thang máy hay không.';
COMMENT ON COLUMN service_order.pickup_has_alley IS 'Điểm đón nằm trong ngõ nhỏ hoặc hạn chế xe tải vào hay không.';
COMMENT ON COLUMN service_order.dropoff_floor IS 'Tầng tại điểm trả; NULL cho dữ liệu cũ hoặc khi khách không cung cấp.';
COMMENT ON COLUMN service_order.dropoff_has_elevator IS 'Điểm trả có thang máy hay không.';
COMMENT ON COLUMN service_order.dropoff_has_alley IS 'Điểm trả nằm trong ngõ nhỏ hoặc hạn chế xe tải vào hay không.';
COMMENT ON COLUMN service_order.base_fare IS 'Cước cơ bản của đơn theo loại xe và quãng đường. NUMERIC(15,0) VND.';
COMMENT ON COLUMN service_order.peak_surcharge IS 'Phụ thu giờ cao điểm. NUMERIC(15,0) VND, mặc định 0 để không vỡ seed.';
COMMENT ON COLUMN service_order.alley_surcharge IS 'Phụ thu ngõ nhỏ. NUMERIC(15,0) VND, mặc định 0 để không vỡ seed.';
COMMENT ON COLUMN service_order.floor_surcharge IS 'Phụ thu tầng cao khi không có thang máy. NUMERIC(15,0) VND, mặc định 0.';
COMMENT ON COLUMN service_order.porter_fee IS 'Phí bốc xếp theo số người hỗ trợ. NUMERIC(15,0) VND, mặc định 0.';
