-- =============================================================================
-- V5: Tao bang service_order — Don hang chuyen nha (thuc the trung tam)
-- Bang ten "service_order" vi "order" la reserved word trong PostgreSQL.
-- AC-07: tat ca timestamp dung TIMESTAMPTZ (luu UTC, hien thi Asia/Ho_Chi_Minh).
-- AC-08: total_quote, commission_rate_snapshot dung NUMERIC (khong float/double).
-- AC-09: soft delete qua deleted_at.
-- Spec #028 FR-006: commission = total_quote * commission_rate_snapshot.
-- =============================================================================

CREATE TABLE service_order (

    id                          UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Ma don hang hien thi (vd: "MH202606010001") — sinh trong service, not DB
    order_code                  VARCHAR(20)     NOT NULL,

    -- Customer dat don (KHONG NULL: don phai co customer)
    customer_id                 UUID            NOT NULL
                                REFERENCES app_user(id),

    -- Driver duoc phan cong — NULL khi chua co Driver (status=PENDING)
    driver_id                   UUID
                                REFERENCES app_user(id),

    -- Dia chi don hang (Ha Noi noi thanh, toi da 12 quan)
    pickup_address              VARCHAR(500)    NOT NULL,
    pickup_district             VARCHAR(100),   -- Ten quan: "Ba Dinh", "Hoan Kiem", v.v.
    dropoff_address             VARCHAR(500)    NOT NULL,
    dropoff_district            VARCHAR(100),

    -- Gio khach hen chuyen nha — dung tinh peak-hour surcharge (AC-07)
    -- Peak: 7:00-9:00 va 17:00-19:00 Asia/Ho_Chi_Minh (CONTEXT.md §2)
    scheduled_at                TIMESTAMPTZ     NOT NULL,

    -- Trang thai vong doi don hang
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN (
                                    'PENDING',      -- Vua tao, chua co Driver
                                    'ACCEPTED',     -- Driver da nhan don
                                    'IN_PROGRESS',  -- Dang van chuyen
                                    'COMPLETED',    -- Hoan thanh, da thanh toan
                                    'CANCELLED',    -- Da huy
                                    'DISPUTED'      -- Dang tranh chap (spec day du: IN_DISPUTE)
                                )),

    -- Tong bao gia da chot: base_price + peak_surcharge + alley + floor + porter (CONTEXT.md §2)
    -- NUMERIC(15,0): VND nguyen dong, khong co xu (AC-08)
    total_quote                 NUMERIC(15,0)   NOT NULL,

    -- Snapshot ty le commission TAI THOI DIEM TAO DON (Mac dinh 30% = 0.3000)
    -- Admin co the thay doi ty le cho don moi, nhung don cu giu ty le cu (CONTEXT.md §2)
    -- Bat buoc cho Admin Dashboard FR-006: revenue = total_quote * commission_rate_snapshot
    commission_rate_snapshot    NUMERIC(5,4)    NOT NULL DEFAULT 0.3000,

    -- Du lieu OSRM: khoang cach tinh bang API, NULL neu dung fallback bang quan→quan (AC-06)
    distance_km                 NUMERIC(6,2),
    estimated_duration_minutes  INTEGER,

    -- Timestamps su kien quan trong
    completed_at                TIMESTAMPTZ,    -- Set khi status → COMPLETED; dung tinh escrow 2h
    cancelled_at                TIMESTAMPTZ,    -- Set khi status → CANCELLED
    cancellation_reason         TEXT,           -- Ly do huy: nhap boi Manager hoac 'SYSTEM_TIMEOUT'

    -- Ghi chu cua Customer cho Driver (vd: "Ngo hep, xe 3 gac vao duoc")
    notes                       TEXT,

    -- Soft delete (AC-09) — KHONG dung DELETE FROM service_order
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ,    -- NULL = chua xoa; co gia tri = da xoa logic

    CONSTRAINT pk_service_order      PRIMARY KEY (id),
    CONSTRAINT uq_service_order_code UNIQUE (order_code)
);


-- Index theo ma don (findByOrderCode — tim don theo ma hien thi cho Customer/Driver)
CREATE INDEX idx_order_code
    ON service_order (order_code);

-- Index cho lich su don cua Customer: GET /api/customer/orders (sort moi nhat truoc)
CREATE INDEX idx_order_customer
    ON service_order (customer_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Index cho don cua Driver: GET /api/driver/orders
CREATE INDEX idx_order_driver
    ON service_order (driver_id, created_at DESC)
    WHERE deleted_at IS NULL AND driver_id IS NOT NULL;

-- Index cho cac query theo status: KPI dashboard + admin filter
CREATE INDEX idx_order_status
    ON service_order (status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Index rieng cho COMPLETED orders (FR-006 dashboard revenue + escrow job)
CREATE INDEX idx_order_completed_at
    ON service_order (completed_at DESC)
    WHERE status = 'COMPLETED' AND deleted_at IS NULL;


-- Trigger tu dong cap nhat updated_at (ham tao tu V1)
CREATE TRIGGER trg_service_order_updated_at
    BEFORE UPDATE ON service_order
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMENT ON TABLE  service_order                        IS 'Don hang chuyen nha. Ten "service_order" de tranh reserved word "order" trong PostgreSQL.';
COMMENT ON COLUMN service_order.total_quote            IS 'Tong bao gia (VND): base + phu thu + porter. NUMERIC(15,0) — AC-08.';
COMMENT ON COLUMN service_order.commission_rate_snapshot IS 'Ty le commission luc tao don. Khong thay doi du Admin doi rate sau. Dashboard FR-006: revenue = total_quote * commission_rate_snapshot.';
COMMENT ON COLUMN service_order.scheduled_at           IS 'Gio khach hen chuyen. UTC store, convert sang Asia/Ho_Chi_Minh khi check peak-hour — AC-07.';
COMMENT ON COLUMN service_order.deleted_at             IS 'Soft delete (AC-09). KHONG dung DELETE FROM service_order.';
