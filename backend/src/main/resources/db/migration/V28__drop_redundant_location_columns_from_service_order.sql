-- V28__drop_redundant_location_columns_from_service_order.sql
-- Don no ky thuat V17: tracking vi tri tai xe da chuyen sang bang driver_location (V20).
-- 3 cot current_lat, current_lng, current_location_recorded_at tren service_order khong con duoc dung.
-- Grep check sach (chi xuat hien o file migration V17 goc, KHONG co code Java/FE nao reference).
-- Spec Sprint 6 M5-T2.
ALTER TABLE service_order
DROP COLUMN IF EXISTS current_lat,
DROP COLUMN IF EXISTS current_lng,
DROP COLUMN IF EXISTS current_location_recorded_at;