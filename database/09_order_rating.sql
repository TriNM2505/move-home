-- =============================================================================
-- Bang: order_rating  (09/32)
-- Tac dung: Danh gia (1-5 sao + nhan xet) cua Customer cho Tai xe sau khi don
--           COMPLETED. Moi don chi duoc danh gia 1 lan (order_id UNIQUE); dung
--           de tinh average_rating cua driver_profile.
-- Nguon migration: V9.
-- Constitution: AC-14 (INTEGER + CHECK), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE order_rating (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL UNIQUE REFERENCES service_order(id),   -- 1 danh gia / don
    customer_id UUID        NOT NULL REFERENCES app_user(id),
    driver_id   UUID        REFERENCES app_user(id),                        -- NULL cho du lieu cu
    stars       INTEGER     NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_rating PRIMARY KEY (id)
);

CREATE INDEX idx_order_rating_driver ON order_rating (driver_id);

COMMENT ON TABLE  order_rating         IS 'Danh gia cua Customer cho don da hoan thanh; nguon tinh average_rating.';
COMMENT ON COLUMN order_rating.order_id IS 'Don duoc danh gia; UNIQUE de moi don chi 1 danh gia.';
