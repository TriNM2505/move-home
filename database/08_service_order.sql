-- =============================================================================
-- Bang: service_order  (08/32)
-- Tac dung: DON HANG CHUYEN NHA — thuc the trung tam. Giu diem di/den, gio hen,
--           loai xe + so boc xep, breakdown gia (base + phu thu + porter),
--           snapshot ty le commission, trang thai vong doi (11 gia tri), va cac
--           moc thoi gian (bat dau/den noi/hoan thanh/tra not 70%/release escrow).
-- Nguon migration: V5 (goc) + V7 (booking + breakdown gia) + V16/V21 (status)
--                  + V25 (started_at) + V30 (2-phase payment + escrow, noi VARCHAR(30))
--                  + V37 (arrived_at). Cot vi tri tam V17 da bi V28 xoa (khong co o day).
-- Constitution: HR-21 (ten tranh reserved word "order"), AC-07 (TIMESTAMPTZ),
--               AC-08 (tien NUMERIC(15,0)), AC-09 (soft delete), AC-14 (VARCHAR+CHECK).
-- Phu thuoc: app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE service_order (
    id                          UUID            NOT NULL DEFAULT gen_random_uuid(),
    order_code                  VARCHAR(20)     NOT NULL,           -- vd "MH202606010001"

    customer_id                 UUID            NOT NULL REFERENCES app_user(id),
    driver_id                   UUID            REFERENCES app_user(id),   -- NULL khi chua co Driver

    -- Diem di / den
    pickup_address              VARCHAR(500)    NOT NULL,
    pickup_district             VARCHAR(100),
    dropoff_address             VARCHAR(500)    NOT NULL,
    dropoff_district            VARCHAR(100),
    pickup_lat                  NUMERIC(10,7),                       -- V7
    pickup_lng                  NUMERIC(10,7),                       -- V7
    dropoff_lat                 NUMERIC(10,7),                       -- V7
    dropoff_lng                 NUMERIC(10,7),                       -- V7
    pickup_floor                INTEGER,                             -- V7
    pickup_has_elevator         BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7
    pickup_has_alley            BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7
    dropoff_floor               INTEGER,                             -- V7
    dropoff_has_elevator        BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7
    dropoff_has_alley           BOOLEAN         NOT NULL DEFAULT FALSE,  -- V7

    scheduled_at                TIMESTAMPTZ     NOT NULL,            -- gio hen (check peak-hour AC-07)

    -- Loai xe + boc xep (V7)
    vehicle_type                VARCHAR(20)     NOT NULL DEFAULT 'TRUCK_500KG',
    porter_count                INTEGER         NOT NULL DEFAULT 0,

    -- Trang thai vong doi (V21: 11 gia tri; con lan cap legacy + moi)
    status                      VARCHAR(30)     NOT NULL DEFAULT 'PENDING',   -- noi VARCHAR(30) o V30

    -- Gia (AC-08)
    total_quote                 NUMERIC(15,0)   NOT NULL,
    commission_rate_snapshot    NUMERIC(5,4)    NOT NULL DEFAULT 0.3000,   -- snapshot luc tao don
    base_fare                   NUMERIC(15,0),                       -- V7
    peak_surcharge              NUMERIC(15,0)   DEFAULT 0,           -- V7
    alley_surcharge             NUMERIC(15,0)   DEFAULT 0,           -- V7
    floor_surcharge             NUMERIC(15,0)   DEFAULT 0,           -- V7
    porter_fee                  NUMERIC(15,0)   DEFAULT 0,           -- V7

    -- OSRM (NULL neu dung fallback bang quan->quan, AC-06)
    distance_km                 NUMERIC(6,2),
    estimated_duration_minutes  INTEGER,

    -- Moc thoi gian su kien
    started_at                  TIMESTAMPTZ,                         -- V25: khi -> IN_PROGRESS
    arrived_at                  TIMESTAMPTZ,                         -- V37: tai xe "Da den diem don"
    completed_at                TIMESTAMPTZ,                         -- khi -> COMPLETED (tinh escrow 2h)
    final_paid_at               TIMESTAMPTZ,                         -- V30: khach tra not 70% (VNPay IPN)
    earning_released_at         TIMESTAMPTZ,                         -- V30: release 70% vao vi tai xe
    cancelled_at                TIMESTAMPTZ,
    cancellation_reason         TEXT,

    notes                       TEXT,                                -- ghi chu Customer cho Driver

    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ,                         -- soft delete (AC-09)

    CONSTRAINT pk_service_order          PRIMARY KEY (id),
    CONSTRAINT uq_service_order_code     UNIQUE (order_code),
    CONSTRAINT ck_service_order_vehicle_type                                   -- V7
        CHECK (vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')),
    CONSTRAINT ck_service_order_status                                         -- V21
        CHECK (status IN (
            'PENDING',
            'PENDING_PAYMENT',
            'CONFIRMED',
            'ASSIGNED',
            'ACCEPTED',
            'IN_PROGRESS',
            'AWAITING_FINAL_PAYMENT',
            'COMPLETED',
            'DISPUTED',              -- alias legacy cua IN_DISPUTE
            'IN_DISPUTE',
            'CANCELLED'
        ))
);

CREATE INDEX idx_order_code         ON service_order (order_code);
CREATE INDEX idx_order_customer     ON service_order (customer_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_driver       ON service_order (driver_id, created_at DESC) WHERE deleted_at IS NULL AND driver_id IS NOT NULL;
CREATE INDEX idx_order_status       ON service_order (status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_completed_at ON service_order (completed_at DESC) WHERE status = 'COMPLETED' AND deleted_at IS NULL;
-- V30: scheduled job escrow quet don COMPLETED chua release qua 2h
CREATE INDEX idx_order_escrow_pending ON service_order (completed_at)
    WHERE status = 'COMPLETED' AND earning_released_at IS NULL AND deleted_at IS NULL;

CREATE TRIGGER trg_service_order_updated_at
    BEFORE UPDATE ON service_order
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  service_order                          IS 'Don hang chuyen nha. Ten "service_order" tranh reserved word "order" (HR-21).';
COMMENT ON COLUMN service_order.total_quote              IS 'Tong bao gia (VND): base + phu thu + porter. NUMERIC(15,0) — AC-08.';
COMMENT ON COLUMN service_order.commission_rate_snapshot IS 'Ty le commission luc tao don; khong doi du Admin doi rate sau (dashboard FR-006).';
COMMENT ON COLUMN service_order.status                   IS '11 gia tri (V21). Con lan cap legacy/moi: PENDING~PENDING_PAYMENT, ASSIGNED~ACCEPTED, DISPUTED~IN_DISPUTE.';
