-- =============================================================================
-- V99: Seed data demo Thu Ba 2026-06-02
-- Tao: 3 staff + 6 drivers + 8 customers + 30 orders + 65 transactions
-- Mat khau chung (BCrypt cost=12 hash cua "Admin@2026"):
--   $2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi
-- =============================================================================

-- ===== 1. USERS (staff + drivers + customers) =====

INSERT INTO app_user (email, password_hash, full_name, phone, role, status,
                      email_verified, terms_accepted, must_change_password, created_at, updated_at)
VALUES
-- Admin
('admin@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Admin He Thong', '+84901000001', 'ADMIN', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '60 days', NOW()),

-- Managers
('manager1@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Nguyen Van Quan Ly', '+84901000002', 'MANAGER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '59 days', NOW()),
('manager2@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Tran Thi Quan Ly', '+84901000003', 'MANAGER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '58 days', NOW()),

-- 5 Active Drivers
('driver1@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Nguyen Van Minh', '+84901001001', 'DRIVER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '35 days', NOW()),
('driver2@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Tran Thanh Hung', '+84901001002', 'DRIVER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '34 days', NOW()),
('driver3@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Le Quang Duc', '+84901001003', 'DRIVER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '33 days', NOW()),
('driver4@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Pham Thi Lan', '+84901001004', 'DRIVER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '32 days', NOW()),
('driver5@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Hoang Van Nam', '+84901001005', 'DRIVER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '31 days', NOW()),

-- 1 Driver dang cho duyet (PENDING_APPROVAL) — de demo Manager review
('driver_pending@movehome.vn',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Vo Thanh Tung', '+84901001006', 'DRIVER', 'PENDING_APPROVAL', true, true, false,
 NOW() - INTERVAL '3 days', NOW()),

-- 8 Active Customers
('customer1@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Do Thi Mai', '+84912001001', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '30 days', NOW()),
('customer2@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Bui Van Long', '+84912001002', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '29 days', NOW()),
('customer3@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Nguyen Thi Hoa', '+84912001003', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '28 days', NOW()),
('customer4@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Pham Van Tuan', '+84912001004', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '27 days', NOW()),
('customer5@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Le Thi Thu', '+84912001005', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '26 days', NOW()),
('customer6@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Tran Van Khanh', '+84912001006', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '25 days', NOW()),
('customer7@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Vu Thi Hue', '+84912001007', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '24 days', NOW()),
('customer8@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Dinh Van Hai', '+84912001008', 'CUSTOMER', 'ACTIVE', true, true, false,
 NOW() - INTERVAL '23 days', NOW()),

-- 2 Customers PENDING_VERIFY (de demo trang thai chua verify)
('customer9@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Ly Van Cuong', '+84912001009', 'CUSTOMER', 'PENDING_VERIFY', false, true, false,
 NOW() - INTERVAL '1 day', NOW()),
('customer10@test.com',
 '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi',
 'Nguyen Van An', '+84912001010', 'CUSTOMER', 'PENDING_VERIFY', false, true, false,
 NOW() - INTERVAL '2 hours', NOW())

ON CONFLICT (email) DO NOTHING;


-- ===== 2. DRIVER PROFILES (5 active + 1 pending) =====

INSERT INTO driver_profile (user_id, license_number, license_class, vehicle_plate, vehicle_type,
                            vehicle_capacity_kg, deposit_amount, deposit_paid_at,
                            approved_at, approved_by_manager_id,
                            total_orders_completed, total_revenue, average_rating,
                            created_at, updated_at)
SELECT
    (SELECT id FROM app_user WHERE email = 'driver1@movehome.vn'),
    'B2-HN-123456', 'B2', '30A-12345', 'Xe tai 500kg',
    500, 3000000, NOW() - INTERVAL '30 days',
    NOW() - INTERVAL '29 days', (SELECT id FROM app_user WHERE email = 'manager1@movehome.vn'),
    12, 16800000, 4.50,
    NOW() - INTERVAL '35 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM driver_profile
                  WHERE user_id = (SELECT id FROM app_user WHERE email = 'driver1@movehome.vn'));

INSERT INTO driver_profile (user_id, license_number, license_class, vehicle_plate, vehicle_type,
                            vehicle_capacity_kg, deposit_amount, deposit_paid_at,
                            approved_at, approved_by_manager_id,
                            total_orders_completed, total_revenue, average_rating,
                            created_at, updated_at)
SELECT
    (SELECT id FROM app_user WHERE email = 'driver2@movehome.vn'),
    'B2-HN-234567', 'B2', '30A-23456', 'Xe tai 1 tan',
    1000, 3000000, NOW() - INTERVAL '29 days',
    NOW() - INTERVAL '28 days', (SELECT id FROM app_user WHERE email = 'manager1@movehome.vn'),
    8, 11200000, 4.80,
    NOW() - INTERVAL '34 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM driver_profile
                  WHERE user_id = (SELECT id FROM app_user WHERE email = 'driver2@movehome.vn'));

INSERT INTO driver_profile (user_id, license_number, license_class, vehicle_plate, vehicle_type,
                            vehicle_capacity_kg, deposit_amount, deposit_paid_at,
                            approved_at, approved_by_manager_id,
                            total_orders_completed, total_revenue, average_rating,
                            created_at, updated_at)
SELECT
    (SELECT id FROM app_user WHERE email = 'driver3@movehome.vn'),
    'C-HN-345678', 'C', '30B-34567', 'Xe tai 1.5 tan',
    1500, 3000000, NOW() - INTERVAL '28 days',
    NOW() - INTERVAL '27 days', (SELECT id FROM app_user WHERE email = 'manager2@movehome.vn'),
    15, 21000000, 4.30,
    NOW() - INTERVAL '33 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM driver_profile
                  WHERE user_id = (SELECT id FROM app_user WHERE email = 'driver3@movehome.vn'));

INSERT INTO driver_profile (user_id, license_number, license_class, vehicle_plate, vehicle_type,
                            vehicle_capacity_kg, deposit_amount, deposit_paid_at,
                            approved_at, approved_by_manager_id,
                            total_orders_completed, total_revenue, average_rating,
                            created_at, updated_at)
SELECT
    (SELECT id FROM app_user WHERE email = 'driver4@movehome.vn'),
    'B2-HN-456789', 'B2', '29A-45678', 'Xe tai 500kg',
    500, 3000000, NOW() - INTERVAL '27 days',
    NOW() - INTERVAL '26 days', (SELECT id FROM app_user WHERE email = 'manager2@movehome.vn'),
    5, 7000000, 4.90,
    NOW() - INTERVAL '32 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM driver_profile
                  WHERE user_id = (SELECT id FROM app_user WHERE email = 'driver4@movehome.vn'));

INSERT INTO driver_profile (user_id, license_number, license_class, vehicle_plate, vehicle_type,
                            vehicle_capacity_kg, deposit_amount, deposit_paid_at,
                            approved_at, approved_by_manager_id,
                            total_orders_completed, total_revenue, average_rating,
                            created_at, updated_at)
SELECT
    (SELECT id FROM app_user WHERE email = 'driver5@movehome.vn'),
    'C-HN-567890', 'C', '30C-56789', 'Xe tai 1 tan',
    1000, 3000000, NOW() - INTERVAL '26 days',
    NOW() - INTERVAL '25 days', (SELECT id FROM app_user WHERE email = 'manager1@movehome.vn'),
    20, 28000000, 4.70,
    NOW() - INTERVAL '31 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM driver_profile
                  WHERE user_id = (SELECT id FROM app_user WHERE email = 'driver5@movehome.vn'));

-- Driver pending (chua duyet — approvedAt = NULL)
INSERT INTO driver_profile (user_id, license_number, license_class, vehicle_plate, vehicle_type,
                            vehicle_capacity_kg, deposit_amount, deposit_paid_at,
                            total_orders_completed, total_revenue, average_rating,
                            created_at, updated_at)
SELECT
    (SELECT id FROM app_user WHERE email = 'driver_pending@movehome.vn'),
    'B2-HN-678901', 'B2', '30D-67890', 'Xe tai 500kg',
    500, 3000000, NOW() - INTERVAL '2 days',
    0, 0, 0.00,
    NOW() - INTERVAL '3 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM driver_profile
                  WHERE user_id = (SELECT id FROM app_user WHERE email = 'driver_pending@movehome.vn'));


-- ===== 3. SERVICE ORDERS (30 tong) =====
-- Rotation customer: C1-C8 theo thu tu | Rotation driver: D1-D5
-- So tien tang dan ve cuoi (de chart co slope di len)

-- COMPLETED orders (20 don, trai 30 ngay — revenue chinh cho dashboard)
INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-001',
    (SELECT id FROM app_user WHERE email = 'customer1@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver1@movehome.vn'),
    '12 Phan Dinh Phung', 'Ba Dinh', '45 Kim Ma', 'Ba Dinh',
    NOW()-INTERVAL '28 days', 'COMPLETED', 2500000, 0.3000, 5.20, 35,
    NOW()-INTERVAL '28 days'+INTERVAL '3 hours', NOW()-INTERVAL '28 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-001');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-002',
    (SELECT id FROM app_user WHERE email = 'customer2@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver2@movehome.vn'),
    '23 Hang Bong', 'Hoan Kiem', '78 Tran Hung Dao', 'Hoan Kiem',
    NOW()-INTERVAL '27 days', 'COMPLETED', 1800000, 0.3000, 3.80, 28,
    NOW()-INTERVAL '27 days'+INTERVAL '2 hours', NOW()-INTERVAL '27 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-002');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-003',
    (SELECT id FROM app_user WHERE email = 'customer3@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver3@movehome.vn'),
    '56 Nguyen Chi Thanh', 'Dong Da', '12 Lang Ha', 'Dong Da',
    NOW()-INTERVAL '26 days', 'COMPLETED', 3200000, 0.3000, 7.50, 45,
    NOW()-INTERVAL '26 days'+INTERVAL '4 hours', NOW()-INTERVAL '26 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-003');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-004',
    (SELECT id FROM app_user WHERE email = 'customer4@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver4@movehome.vn'),
    '34 Xuan Thuy', 'Cau Giay', '89 Nguyen Van Huyen', 'Cau Giay',
    NOW()-INTERVAL '24 days', 'COMPLETED', 1500000, 0.3000, 3.20, 25,
    NOW()-INTERVAL '24 days'+INTERVAL '2 hours', NOW()-INTERVAL '24 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-004');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-005',
    (SELECT id FROM app_user WHERE email = 'customer5@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver5@movehome.vn'),
    '90 Ho Tay', 'Tay Ho', '23 Nguyen Phong Sac', 'Cau Giay',
    NOW()-INTERVAL '22 days', 'COMPLETED', 4000000, 0.3000, 9.80, 60,
    NOW()-INTERVAL '22 days'+INTERVAL '5 hours', NOW()-INTERVAL '22 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-005');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-006',
    (SELECT id FROM app_user WHERE email = 'customer6@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver1@movehome.vn'),
    '15 Kham Thien', 'Dong Da', '67 Le Duan', 'Dong Da',
    NOW()-INTERVAL '21 days', 'COMPLETED', 2800000, 0.3000, 6.10, 40,
    NOW()-INTERVAL '21 days'+INTERVAL '3 hours', NOW()-INTERVAL '21 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-006');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-007',
    (SELECT id FROM app_user WHERE email = 'customer7@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver2@movehome.vn'),
    '44 Bach Mai', 'Hai Ba Trung', '12 Minh Khai', 'Hai Ba Trung',
    NOW()-INTERVAL '19 days', 'COMPLETED', 1900000, 0.3000, 4.00, 30,
    NOW()-INTERVAL '19 days'+INTERVAL '2 hours', NOW()-INTERVAL '19 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-007');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-008',
    (SELECT id FROM app_user WHERE email = 'customer8@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver3@movehome.vn'),
    '78 Nguyen Luong Bang', 'Dong Da', '34 Ton Duc Thang', 'Dong Da',
    NOW()-INTERVAL '17 days', 'COMPLETED', 3500000, 0.3000, 8.20, 50,
    NOW()-INTERVAL '17 days'+INTERVAL '4 hours', NOW()-INTERVAL '17 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-008');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-009',
    (SELECT id FROM app_user WHERE email = 'customer1@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver4@movehome.vn'),
    '56 Dinh Tien Hoang', 'Hoan Kiem', '90 Hang Bai', 'Hoan Kiem',
    NOW()-INTERVAL '15 days', 'COMPLETED', 2200000, 0.3000, 4.70, 32,
    NOW()-INTERVAL '15 days'+INTERVAL '3 hours', NOW()-INTERVAL '15 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-009');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-010',
    (SELECT id FROM app_user WHERE email = 'customer2@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver5@movehome.vn'),
    '23 Nguyen Hue', 'Hoan Kiem', '45 Le Loi', 'Hoan Kiem',
    NOW()-INTERVAL '14 days', 'COMPLETED', 1600000, 0.3000, 3.40, 26,
    NOW()-INTERVAL '14 days'+INTERVAL '2 hours', NOW()-INTERVAL '14 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-010');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-011',
    (SELECT id FROM app_user WHERE email = 'customer3@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver1@movehome.vn'),
    '12 Kim Lien', 'Dong Da', '56 Le Thanh Nghi', 'Hai Ba Trung',
    NOW()-INTERVAL '12 days', 'COMPLETED', 3800000, 0.3000, 8.90, 55,
    NOW()-INTERVAL '12 days'+INTERVAL '4 hours', NOW()-INTERVAL '12 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-011');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-012',
    (SELECT id FROM app_user WHERE email = 'customer4@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver2@movehome.vn'),
    '34 Thanh Xuan', 'Thanh Xuan', '78 Nguyen Trai', 'Thanh Xuan',
    NOW()-INTERVAL '10 days', 'COMPLETED', 2600000, 0.3000, 5.60, 38,
    NOW()-INTERVAL '10 days'+INTERVAL '3 hours', NOW()-INTERVAL '10 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-012');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-013',
    (SELECT id FROM app_user WHERE email = 'customer5@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver3@movehome.vn'),
    '67 Hoang Hoa Tham', 'Ba Dinh', '23 Lieu Giai', 'Ba Dinh',
    NOW()-INTERVAL '9 days', 'COMPLETED', 4200000, 0.3000, 9.90, 62,
    NOW()-INTERVAL '9 days'+INTERVAL '5 hours', NOW()-INTERVAL '9 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-013');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-014',
    (SELECT id FROM app_user WHERE email = 'customer6@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver4@movehome.vn'),
    '89 Giang Vo', 'Ba Dinh', '45 Nui Truc', 'Ba Dinh',
    NOW()-INTERVAL '7 days', 'COMPLETED', 1750000, 0.3000, 3.70, 28,
    NOW()-INTERVAL '7 days'+INTERVAL '2 hours', NOW()-INTERVAL '7 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-014');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-015',
    (SELECT id FROM app_user WHERE email = 'customer7@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver5@movehome.vn'),
    '12 Trung Hoa', 'Cau Giay', '56 Duy Tan', 'Cau Giay',
    NOW()-INTERVAL '6 days', 'COMPLETED', 3100000, 0.3000, 6.80, 44,
    NOW()-INTERVAL '6 days'+INTERVAL '3 hours', NOW()-INTERVAL '6 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-015');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-016',
    (SELECT id FROM app_user WHERE email = 'customer8@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver1@movehome.vn'),
    '45 Hoang Quoc Viet', 'Cau Giay', '89 Nguyen Khanh Toan', 'Cau Giay',
    NOW()-INTERVAL '5 days', 'COMPLETED', 2900000, 0.3000, 6.30, 42,
    NOW()-INTERVAL '5 days'+INTERVAL '3 hours', NOW()-INTERVAL '5 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-016');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-017',
    (SELECT id FROM app_user WHERE email = 'customer1@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver2@movehome.vn'),
    '23 Tran Duy Hung', 'Cau Giay', '67 Vo Chi Cong', 'Tay Ho',
    NOW()-INTERVAL '4 days', 'COMPLETED', 4500000, 0.3000, 10.50, 65,
    NOW()-INTERVAL '4 days'+INTERVAL '5 hours', NOW()-INTERVAL '4 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-017');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-018',
    (SELECT id FROM app_user WHERE email = 'customer2@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver3@movehome.vn'),
    '56 Au Co', 'Tay Ho', '12 Thanh Nien', 'Tay Ho',
    NOW()-INTERVAL '3 days', 'COMPLETED', 3300000, 0.3000, 7.20, 48,
    NOW()-INTERVAL '3 days'+INTERVAL '4 hours', NOW()-INTERVAL '3 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-018');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-019',
    (SELECT id FROM app_user WHERE email = 'customer3@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver4@movehome.vn'),
    '34 Nguyen Van Cu', 'Hai Ba Trung', '78 Truong Dinh', 'Hai Ba Trung',
    NOW()-INTERVAL '2 days', 'COMPLETED', 2700000, 0.3000, 5.90, 40,
    NOW()-INTERVAL '2 days'+INTERVAL '3 hours', NOW()-INTERVAL '2 days', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-019');

INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    completed_at, created_at, updated_at)
SELECT 'SEED-C-020',
    (SELECT id FROM app_user WHERE email = 'customer4@test.com'),
    (SELECT id FROM app_user WHERE email = 'driver5@movehome.vn'),
    '90 Giai Phong', 'Hai Ba Trung', '23 Truong Trinh', 'Thanh Xuan',
    NOW()-INTERVAL '1 day', 'COMPLETED', 5000000, 0.3000, 11.20, 70,
    NOW()-INTERVAL '1 day'+INTERVAL '5 hours', NOW()-INTERVAL '1 day', NOW()
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code='SEED-C-020');


-- IN_PROGRESS orders (5 don dang van chuyen)
INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    created_at, updated_at)
SELECT code, cust, drv, pa, pd, da, dd, sch, 'IN_PROGRESS', tq, 0.3000, dk, dur, cr, NOW()
FROM (VALUES
    ('SEED-IP-001',
     (SELECT id FROM app_user WHERE email='customer5@test.com'),
     (SELECT id FROM app_user WHERE email='driver1@movehome.vn'),
     '12 Ha Huy Tap','Hai Ba Trung','56 Vinh Tuy','Hai Ba Trung',
     NOW()-INTERVAL '2 hours', 2200000, 6.10, 40, NOW()-INTERVAL '3 hours'),
    ('SEED-IP-002',
     (SELECT id FROM app_user WHERE email='customer6@test.com'),
     (SELECT id FROM app_user WHERE email='driver2@movehome.vn'),
     '34 Lang','Dong Da','78 Chua Lang','Dong Da',
     NOW()-INTERVAL '1 hour', 1800000, 4.20, 30, NOW()-INTERVAL '2 hours'),
    ('SEED-IP-003',
     (SELECT id FROM app_user WHERE email='customer7@test.com'),
     (SELECT id FROM app_user WHERE email='driver3@movehome.vn'),
     '56 Tran Quoc Hoan','Cau Giay','12 Nghia Do','Cau Giay',
     NOW()-INTERVAL '90 minutes', 3200000, 7.50, 50, NOW()-INTERVAL '2 hours'),
    ('SEED-IP-004',
     (SELECT id FROM app_user WHERE email='customer8@test.com'),
     (SELECT id FROM app_user WHERE email='driver4@movehome.vn'),
     '78 Nguyen Khuyen','Dong Da','23 Tran Hung Dao','Hoan Kiem',
     NOW()-INTERVAL '45 minutes', 2800000, 6.50, 42, NOW()-INTERVAL '90 minutes'),
    ('SEED-IP-005',
     (SELECT id FROM app_user WHERE email='customer1@test.com'),
     (SELECT id FROM app_user WHERE email='driver5@movehome.vn'),
     '90 Lac Long Quan','Tay Ho','45 Buoi','Tay Ho',
     NOW()-INTERVAL '30 minutes', 4100000, 9.50, 60, NOW()-INTERVAL '1 hour')
) AS t(code, cust, drv, pa, pd, da, dd, sch, tq, dk, dur, cr)
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code = code);


-- PENDING orders (3 don chua co driver)
INSERT INTO service_order (order_code, customer_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    created_at, updated_at)
SELECT code, cust, pa, pd, da, dd, sch, 'PENDING', tq, 0.3000, dk, dur, cr, NOW()
FROM (VALUES
    ('SEED-P-001',
     (SELECT id FROM app_user WHERE email='customer2@test.com'),
     '12 Phan Chu Trinh','Hoan Kiem','56 Dinh Le','Hoan Kiem',
     NOW()+INTERVAL '2 hours', 1500000, 3.20, 25, NOW()-INTERVAL '30 minutes'),
    ('SEED-P-002',
     (SELECT id FROM app_user WHERE email='customer3@test.com'),
     '34 Truong Han Sieu','Hoan Kiem','78 Tran Nhan Tong','Hai Ba Trung',
     NOW()+INTERVAL '4 hours', 2800000, 6.10, 40, NOW()-INTERVAL '15 minutes'),
    ('SEED-P-003',
     (SELECT id FROM app_user WHERE email='customer4@test.com'),
     '67 Tay Son','Dong Da','23 Nguyen Luong Bang','Dong Da',
     NOW()+INTERVAL '1 day', 2100000, 4.80, 33, NOW()-INTERVAL '5 minutes')
) AS t(code, cust, pa, pd, da, dd, sch, tq, dk, dur, cr)
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code = code);


-- CANCELLED orders (2 don da huy)
INSERT INTO service_order (order_code, customer_id, driver_id,
    pickup_address, pickup_district, dropoff_address, dropoff_district,
    scheduled_at, status, total_quote, commission_rate_snapshot, distance_km, estimated_duration_minutes,
    cancelled_at, cancellation_reason, created_at, updated_at)
SELECT code, cust, drv, pa, pd, da, dd, sch, 'CANCELLED', tq, 0.3000, dk, dur, ca, cr_reason, cr, NOW()
FROM (VALUES
    ('SEED-X-001',
     (SELECT id FROM app_user WHERE email='customer5@test.com'),
     NULL::UUID,
     '12 Hang Dao','Hoan Kiem','56 Hang Gai','Hoan Kiem',
     NOW()-INTERVAL '10 days', 1200000, 2.50, 20,
     NOW()-INTERVAL '10 days'+INTERVAL '30 minutes',
     'Khach huy don truoc khi co driver', NOW()-INTERVAL '10 days'),
    ('SEED-X-002',
     (SELECT id FROM app_user WHERE email='customer6@test.com'),
     (SELECT id FROM app_user WHERE email='driver1@movehome.vn'),
     '34 Hang Bac','Hoan Kiem','78 Hang Bo','Hoan Kiem',
     NOW()-INTERVAL '5 days', 1800000, 3.80, 28,
     NOW()-INTERVAL '5 days'+INTERVAL '2 hours',
     'Khong tim duoc xe phu hop', NOW()-INTERVAL '5 days')
) AS t(code, cust, drv, pa, pd, da, dd, sch, tq, dk, dur, ca, cr_reason, cr)
WHERE NOT EXISTS (SELECT 1 FROM service_order WHERE order_code = code);


-- ===== 4. TRANSACTIONS =====

-- DEPOSIT_TOP_UP: 5 active drivers dong coc 3 trieu moi nguoi
INSERT INTO transaction (user_id, type, amount, description, created_at)
SELECT id, 'DEPOSIT_TOP_UP', 3000000, 'Dong coc dang ky lam tai xe', NOW()-INTERVAL '30 days'
FROM app_user WHERE email='driver1@movehome.vn'
AND NOT EXISTS (SELECT 1 FROM transaction
                WHERE user_id=(SELECT id FROM app_user WHERE email='driver1@movehome.vn')
                AND type='DEPOSIT_TOP_UP');

INSERT INTO transaction (user_id, type, amount, description, created_at)
SELECT id, 'DEPOSIT_TOP_UP', 3000000, 'Dong coc dang ky lam tai xe', NOW()-INTERVAL '29 days'
FROM app_user WHERE email='driver2@movehome.vn'
AND NOT EXISTS (SELECT 1 FROM transaction
                WHERE user_id=(SELECT id FROM app_user WHERE email='driver2@movehome.vn')
                AND type='DEPOSIT_TOP_UP');

INSERT INTO transaction (user_id, type, amount, description, created_at)
SELECT id, 'DEPOSIT_TOP_UP', 3000000, 'Dong coc dang ky lam tai xe', NOW()-INTERVAL '28 days'
FROM app_user WHERE email='driver3@movehome.vn'
AND NOT EXISTS (SELECT 1 FROM transaction
                WHERE user_id=(SELECT id FROM app_user WHERE email='driver3@movehome.vn')
                AND type='DEPOSIT_TOP_UP');

INSERT INTO transaction (user_id, type, amount, description, created_at)
SELECT id, 'DEPOSIT_TOP_UP', 3000000, 'Dong coc dang ky lam tai xe', NOW()-INTERVAL '27 days'
FROM app_user WHERE email='driver4@movehome.vn'
AND NOT EXISTS (SELECT 1 FROM transaction
                WHERE user_id=(SELECT id FROM app_user WHERE email='driver4@movehome.vn')
                AND type='DEPOSIT_TOP_UP');

INSERT INTO transaction (user_id, type, amount, description, created_at)
SELECT id, 'DEPOSIT_TOP_UP', 3000000, 'Dong coc dang ky lam tai xe', NOW()-INTERVAL '26 days'
FROM app_user WHERE email='driver5@movehome.vn'
AND NOT EXISTS (SELECT 1 FROM transaction
                WHERE user_id=(SELECT id FROM app_user WHERE email='driver5@movehome.vn')
                AND type='DEPOSIT_TOP_UP');

INSERT INTO transaction (user_id, type, amount, description, created_at)
SELECT id, 'DEPOSIT_TOP_UP', 3000000, 'Dong coc dang ky lam tai xe', NOW()-INTERVAL '2 days'
FROM app_user WHERE email='driver_pending@movehome.vn'
AND NOT EXISTS (SELECT 1 FROM transaction
                WHERE user_id=(SELECT id FROM app_user WHERE email='driver_pending@movehome.vn')
                AND type='DEPOSIT_TOP_UP');


-- ORDER_PAYMENT + DRIVER_EARNING + PLATFORM_FEE cho 20 COMPLETED orders
-- Cau truc: 3 transactions moi order (customer tra, driver nhan 70%, platform giu 30%)

INSERT INTO transaction (user_id, type, amount, related_order_id, description, created_at)
SELECT * FROM (VALUES
    ((SELECT id FROM app_user WHERE email='customer1@test.com'), 'ORDER_PAYMENT'::VARCHAR, 2500000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-001'), 'KH thanh toan don SEED-C-001'::VARCHAR, NOW()-INTERVAL '28 days'),
    ((SELECT id FROM app_user WHERE email='driver1@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1750000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-001'), '70% thu nhap don SEED-C-001'::VARCHAR,    NOW()-INTERVAL '28 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   750000::NUMERIC,  (SELECT id FROM service_order WHERE order_code='SEED-C-001'), '30% phi nen tang don SEED-C-001'::VARCHAR, NOW()-INTERVAL '28 days'),

    ((SELECT id FROM app_user WHERE email='customer2@test.com'), 'ORDER_PAYMENT'::VARCHAR, 1800000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-002'), 'KH thanh toan don SEED-C-002'::VARCHAR, NOW()-INTERVAL '27 days'),
    ((SELECT id FROM app_user WHERE email='driver2@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1260000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-002'), '70% thu nhap don SEED-C-002'::VARCHAR,    NOW()-INTERVAL '27 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    540000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-002'), '30% phi nen tang don SEED-C-002'::VARCHAR, NOW()-INTERVAL '27 days'),

    ((SELECT id FROM app_user WHERE email='customer3@test.com'), 'ORDER_PAYMENT'::VARCHAR, 3200000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-003'), 'KH thanh toan don SEED-C-003'::VARCHAR, NOW()-INTERVAL '26 days'),
    ((SELECT id FROM app_user WHERE email='driver3@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2240000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-003'), '70% thu nhap don SEED-C-003'::VARCHAR,    NOW()-INTERVAL '26 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    960000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-003'), '30% phi nen tang don SEED-C-003'::VARCHAR, NOW()-INTERVAL '26 days'),

    ((SELECT id FROM app_user WHERE email='customer4@test.com'), 'ORDER_PAYMENT'::VARCHAR, 1500000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-004'), 'KH thanh toan don SEED-C-004'::VARCHAR, NOW()-INTERVAL '24 days'),
    ((SELECT id FROM app_user WHERE email='driver4@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1050000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-004'), '70% thu nhap don SEED-C-004'::VARCHAR,    NOW()-INTERVAL '24 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    450000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-004'), '30% phi nen tang don SEED-C-004'::VARCHAR, NOW()-INTERVAL '24 days'),

    ((SELECT id FROM app_user WHERE email='customer5@test.com'), 'ORDER_PAYMENT'::VARCHAR, 4000000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-005'), 'KH thanh toan don SEED-C-005'::VARCHAR, NOW()-INTERVAL '22 days'),
    ((SELECT id FROM app_user WHERE email='driver5@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2800000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-005'), '70% thu nhap don SEED-C-005'::VARCHAR,    NOW()-INTERVAL '22 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   1200000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-005'), '30% phi nen tang don SEED-C-005'::VARCHAR, NOW()-INTERVAL '22 days'),

    ((SELECT id FROM app_user WHERE email='customer6@test.com'), 'ORDER_PAYMENT'::VARCHAR, 2800000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-006'), 'KH thanh toan don SEED-C-006'::VARCHAR, NOW()-INTERVAL '21 days'),
    ((SELECT id FROM app_user WHERE email='driver1@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1960000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-006'), '70% thu nhap don SEED-C-006'::VARCHAR,    NOW()-INTERVAL '21 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    840000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-006'), '30% phi nen tang don SEED-C-006'::VARCHAR, NOW()-INTERVAL '21 days'),

    ((SELECT id FROM app_user WHERE email='customer7@test.com'), 'ORDER_PAYMENT'::VARCHAR, 1900000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-007'), 'KH thanh toan don SEED-C-007'::VARCHAR, NOW()-INTERVAL '19 days'),
    ((SELECT id FROM app_user WHERE email='driver2@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1330000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-007'), '70% thu nhap don SEED-C-007'::VARCHAR,    NOW()-INTERVAL '19 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    570000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-007'), '30% phi nen tang don SEED-C-007'::VARCHAR, NOW()-INTERVAL '19 days'),

    ((SELECT id FROM app_user WHERE email='customer8@test.com'), 'ORDER_PAYMENT'::VARCHAR, 3500000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-008'), 'KH thanh toan don SEED-C-008'::VARCHAR, NOW()-INTERVAL '17 days'),
    ((SELECT id FROM app_user WHERE email='driver3@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2450000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-008'), '70% thu nhap don SEED-C-008'::VARCHAR,    NOW()-INTERVAL '17 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   1050000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-008'), '30% phi nen tang don SEED-C-008'::VARCHAR, NOW()-INTERVAL '17 days'),

    ((SELECT id FROM app_user WHERE email='customer1@test.com'), 'ORDER_PAYMENT'::VARCHAR, 2200000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-009'), 'KH thanh toan don SEED-C-009'::VARCHAR, NOW()-INTERVAL '15 days'),
    ((SELECT id FROM app_user WHERE email='driver4@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1540000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-009'), '70% thu nhap don SEED-C-009'::VARCHAR,    NOW()-INTERVAL '15 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    660000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-009'), '30% phi nen tang don SEED-C-009'::VARCHAR, NOW()-INTERVAL '15 days'),

    ((SELECT id FROM app_user WHERE email='customer2@test.com'), 'ORDER_PAYMENT'::VARCHAR, 1600000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-010'), 'KH thanh toan don SEED-C-010'::VARCHAR, NOW()-INTERVAL '14 days'),
    ((SELECT id FROM app_user WHERE email='driver5@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1120000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-010'), '70% thu nhap don SEED-C-010'::VARCHAR,    NOW()-INTERVAL '14 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    480000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-010'), '30% phi nen tang don SEED-C-010'::VARCHAR, NOW()-INTERVAL '14 days')
) AS t(user_id, type, amount, related_order_id, description, created_at)
WHERE NOT EXISTS (
    SELECT 1 FROM transaction tx
    WHERE tx.related_order_id = t.related_order_id AND tx.type = t.type
          AND tx.user_id = t.user_id
);

INSERT INTO transaction (user_id, type, amount, related_order_id, description, created_at)
SELECT * FROM (VALUES
    ((SELECT id FROM app_user WHERE email='customer3@test.com'), 'ORDER_PAYMENT'::VARCHAR, 3800000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-011'), 'KH thanh toan don SEED-C-011'::VARCHAR, NOW()-INTERVAL '12 days'),
    ((SELECT id FROM app_user WHERE email='driver1@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2660000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-011'), '70% thu nhap don SEED-C-011'::VARCHAR,    NOW()-INTERVAL '12 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   1140000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-011'), '30% phi nen tang don SEED-C-011'::VARCHAR, NOW()-INTERVAL '12 days'),

    ((SELECT id FROM app_user WHERE email='customer4@test.com'), 'ORDER_PAYMENT'::VARCHAR, 2600000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-012'), 'KH thanh toan don SEED-C-012'::VARCHAR, NOW()-INTERVAL '10 days'),
    ((SELECT id FROM app_user WHERE email='driver2@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1820000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-012'), '70% thu nhap don SEED-C-012'::VARCHAR,    NOW()-INTERVAL '10 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    780000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-012'), '30% phi nen tang don SEED-C-012'::VARCHAR, NOW()-INTERVAL '10 days'),

    ((SELECT id FROM app_user WHERE email='customer5@test.com'), 'ORDER_PAYMENT'::VARCHAR, 4200000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-013'), 'KH thanh toan don SEED-C-013'::VARCHAR, NOW()-INTERVAL '9 days'),
    ((SELECT id FROM app_user WHERE email='driver3@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2940000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-013'), '70% thu nhap don SEED-C-013'::VARCHAR,    NOW()-INTERVAL '9 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   1260000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-013'), '30% phi nen tang don SEED-C-013'::VARCHAR, NOW()-INTERVAL '9 days'),

    ((SELECT id FROM app_user WHERE email='customer6@test.com'), 'ORDER_PAYMENT'::VARCHAR, 1750000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-014'), 'KH thanh toan don SEED-C-014'::VARCHAR, NOW()-INTERVAL '7 days'),
    ((SELECT id FROM app_user WHERE email='driver4@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1225000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-014'), '70% thu nhap don SEED-C-014'::VARCHAR,    NOW()-INTERVAL '7 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    525000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-014'), '30% phi nen tang don SEED-C-014'::VARCHAR, NOW()-INTERVAL '7 days'),

    ((SELECT id FROM app_user WHERE email='customer7@test.com'), 'ORDER_PAYMENT'::VARCHAR, 3100000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-015'), 'KH thanh toan don SEED-C-015'::VARCHAR, NOW()-INTERVAL '6 days'),
    ((SELECT id FROM app_user WHERE email='driver5@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2170000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-015'), '70% thu nhap don SEED-C-015'::VARCHAR,    NOW()-INTERVAL '6 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    930000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-015'), '30% phi nen tang don SEED-C-015'::VARCHAR, NOW()-INTERVAL '6 days'),

    ((SELECT id FROM app_user WHERE email='customer8@test.com'), 'ORDER_PAYMENT'::VARCHAR, 2900000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-016'), 'KH thanh toan don SEED-C-016'::VARCHAR, NOW()-INTERVAL '5 days'),
    ((SELECT id FROM app_user WHERE email='driver1@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2030000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-016'), '70% thu nhap don SEED-C-016'::VARCHAR,    NOW()-INTERVAL '5 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    870000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-016'), '30% phi nen tang don SEED-C-016'::VARCHAR, NOW()-INTERVAL '5 days'),

    ((SELECT id FROM app_user WHERE email='customer1@test.com'), 'ORDER_PAYMENT'::VARCHAR, 4500000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-017'), 'KH thanh toan don SEED-C-017'::VARCHAR, NOW()-INTERVAL '4 days'),
    ((SELECT id FROM app_user WHERE email='driver2@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 3150000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-017'), '70% thu nhap don SEED-C-017'::VARCHAR,    NOW()-INTERVAL '4 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   1350000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-017'), '30% phi nen tang don SEED-C-017'::VARCHAR, NOW()-INTERVAL '4 days'),

    ((SELECT id FROM app_user WHERE email='customer2@test.com'), 'ORDER_PAYMENT'::VARCHAR, 3300000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-018'), 'KH thanh toan don SEED-C-018'::VARCHAR, NOW()-INTERVAL '3 days'),
    ((SELECT id FROM app_user WHERE email='driver3@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 2310000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-018'), '70% thu nhap don SEED-C-018'::VARCHAR,    NOW()-INTERVAL '3 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    990000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-018'), '30% phi nen tang don SEED-C-018'::VARCHAR, NOW()-INTERVAL '3 days'),

    ((SELECT id FROM app_user WHERE email='customer3@test.com'), 'ORDER_PAYMENT'::VARCHAR, 2700000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-019'), 'KH thanh toan don SEED-C-019'::VARCHAR, NOW()-INTERVAL '2 days'),
    ((SELECT id FROM app_user WHERE email='driver4@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 1890000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-019'), '70% thu nhap don SEED-C-019'::VARCHAR,    NOW()-INTERVAL '2 days'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,    810000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-019'), '30% phi nen tang don SEED-C-019'::VARCHAR, NOW()-INTERVAL '2 days'),

    ((SELECT id FROM app_user WHERE email='customer4@test.com'), 'ORDER_PAYMENT'::VARCHAR, 5000000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-020'), 'KH thanh toan don SEED-C-020'::VARCHAR, NOW()-INTERVAL '1 day'),
    ((SELECT id FROM app_user WHERE email='driver5@movehome.vn'),  'DRIVER_EARNING'::VARCHAR, 3500000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-020'), '70% thu nhap don SEED-C-020'::VARCHAR,    NOW()-INTERVAL '1 day'),
    ((SELECT id FROM app_user WHERE email='admin@movehome.vn'),    'PLATFORM_FEE'::VARCHAR,   1500000::NUMERIC, (SELECT id FROM service_order WHERE order_code='SEED-C-020'), '30% phi nen tang don SEED-C-020'::VARCHAR, NOW()-INTERVAL '1 day')
) AS t(user_id, type, amount, related_order_id, description, created_at)
WHERE NOT EXISTS (
    SELECT 1 FROM transaction tx
    WHERE tx.related_order_id = t.related_order_id AND tx.type = t.type
          AND tx.user_id = t.user_id
);
-- =============================================================================
-- Ket qua mong doi sau khi chay V99:
--   app_user:       20 rows (3 staff + 6 drivers + 10 customers + 1 pending driver)
--   driver_profile:  6 rows
--   service_order:  30 rows (20 COMPLETED + 5 IN_PROGRESS + 3 PENDING + 2 CANCELLED)
--   transaction:    66 rows (60 order txns + 6 deposit txns)
-- Doanh thu tong (COMPLETED): 57,550,000 VND
-- Commission tong (30%):      17,265,000 VND
-- =============================================================================
