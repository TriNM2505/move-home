-- V17: them cot vi tri hien tai vao service_order
-- Tao lai de khop migration da ap tren Neon (checksum se can bang bang flyway.repair()).
-- LUU Y: thiet ke nay se duoc thay bang bang driver_location o V20 (dung spec #003/#006).
ALTER TABLE service_order ADD COLUMN current_lat NUMERIC;
ALTER TABLE service_order ADD COLUMN current_lng NUMERIC;
ALTER TABLE service_order ADD COLUMN current_location_recorded_at TIMESTAMPTZ;