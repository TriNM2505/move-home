-- =============================================================================
-- Bang: driver_location  (10/32)
-- Tac dung: Luu VI TRI MOI NHAT cua moi Tai xe (1 dong/tai xe, UPSERT theo
--           driver_id). Customer poll de theo doi don dang giao. Thay cho cac cot
--           vi tri tam tren service_order (V17 -> da xoa o V28).
-- Nguon migration: V20.
-- Constitution: AC-07 (TIMESTAMPTZ). Spec #003/#006.
-- =============================================================================

CREATE TABLE driver_location (
    driver_id        UUID          NOT NULL REFERENCES app_user(id),
    current_order_id UUID          REFERENCES service_order(id),
    lat              NUMERIC(10,7) NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng              NUMERIC(10,7) NOT NULL CHECK (lng BETWEEN -180 AND 180),
    heading          NUMERIC(5,2)  CHECK (heading IS NULL OR (heading >= 0 AND heading < 360)),
    speed_kmh        NUMERIC(6,2)  CHECK (speed_kmh IS NULL OR (speed_kmh >= 0 AND speed_kmh <= 180)),
    recorded_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_location PRIMARY KEY (driver_id)
);

COMMENT ON TABLE driver_location IS 'Vi tri moi nhat cua tai xe (UPSERT theo driver_id) cho Customer theo doi don.';
