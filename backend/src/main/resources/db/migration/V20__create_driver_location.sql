-- V20: bang luu vi tri moi nhat cua tai xe (Spec #003 + #006). UPSERT theo driver_id.
-- Thay cho cot tam tren service_order (V17). KHONG drop cot V17 voi (don sau).
CREATE TABLE driver_location (
    driver_id         UUID          NOT NULL REFERENCES app_user(id),
    current_order_id  UUID          REFERENCES service_order(id),
    lat               NUMERIC(10,7) NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng               NUMERIC(10,7) NOT NULL CHECK (lng BETWEEN -180 AND 180),
    heading           NUMERIC(5,2)  CHECK (heading IS NULL OR (heading >= 0 AND heading < 360)),
    speed_kmh         NUMERIC(6,2)  CHECK (speed_kmh IS NULL OR (speed_kmh >= 0 AND speed_kmh <= 180)),
    recorded_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_driver_location PRIMARY KEY (driver_id)
);