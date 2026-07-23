-- =============================================================================
-- Bang: conversation  (22/32)
-- Tac dung: Hoi thoai chat 3 cap (CUSTOMER_MANAGER / MANAGER_DRIVER /
--           CUSTOMER_DRIVER). Gan theo don (order_id); rieng CUSTOMER_MANAGER
--           voi order_id NULL la kenh ho tro chung (moi khach 1 thread). Giu
--           snapshot tin nhan cuoi de hien danh sach hoi thoai nhanh.
-- Nguon migration: V36.
-- Constitution: AC-05 (chat STOMP+SockJS), AC-14 (VARCHAR+CHECK), AC-07 (TIMESTAMPTZ),
--               HR-21 (khong reserved word).
-- =============================================================================

CREATE TABLE conversation (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID        REFERENCES service_order(id),
    type              VARCHAR(20) NOT NULL CHECK (type IN ('CUSTOMER_MANAGER', 'MANAGER_DRIVER', 'CUSTOMER_DRIVER')),
    customer_id       UUID        REFERENCES app_user(id),
    driver_id         UUID        REFERENCES app_user(id),
    last_message_text TEXT,
    last_message_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Moi don chi 1 hoi thoai/loai (chong tao trung)
CREATE UNIQUE INDEX uq_conversation_order_type ON conversation (order_id, type) WHERE order_id IS NOT NULL;
-- Kenh ho tro chung: moi khach chi 1 thread CUSTOMER_MANAGER (order_id NULL)
CREATE UNIQUE INDEX uq_conversation_support ON conversation (customer_id) WHERE type = 'CUSTOMER_MANAGER' AND order_id IS NULL;
CREATE INDEX idx_conversation_customer ON conversation (customer_id, last_message_at DESC);
CREATE INDEX idx_conversation_driver   ON conversation (driver_id, last_message_at DESC);
CREATE INDEX idx_conversation_type     ON conversation (type, last_message_at DESC);

COMMENT ON TABLE conversation IS 'Hoi thoai chat 3 cap; gan theo don hoac kenh ho tro chung (order_id NULL).';
