-- ============================================================
-- V36: He thong chat 3 cap (Customer <-> Manager <-> Driver)
-- Gan theo don (order_id), rieng CUSTOMER_MANAGER la kenh ho tro chung (order_id NULL).
-- Constitution: AC-14 (VARCHAR + CHECK, khong PostgreSQL ENUM), AC-07 (TIMESTAMPTZ luu UTC),
--               HR-21 (bang khong trung reserved word: "conversation", "chat_message").
-- ============================================================

CREATE TABLE conversation (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID REFERENCES service_order(id),
    type              VARCHAR(20) NOT NULL
        CHECK (type IN ('CUSTOMER_MANAGER', 'MANAGER_DRIVER', 'CUSTOMER_DRIVER')),
    customer_id       UUID REFERENCES app_user(id),
    driver_id         UUID REFERENCES app_user(id),
    last_message_text TEXT,
    last_message_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Moi don chi 1 hoi thoai cho moi loai (chong tao trung khi 2 ben cung bam mo)
CREATE UNIQUE INDEX uq_conversation_order_type
    ON conversation (order_id, type)
    WHERE order_id IS NOT NULL;

-- Kenh ho tro chung: moi khach chi 1 thread CUSTOMER_MANAGER (order_id NULL)
CREATE UNIQUE INDEX uq_conversation_support
    ON conversation (customer_id)
    WHERE type = 'CUSTOMER_MANAGER' AND order_id IS NULL;

CREATE INDEX idx_conversation_customer ON conversation (customer_id, last_message_at DESC);
CREATE INDEX idx_conversation_driver   ON conversation (driver_id, last_message_at DESC);
CREATE INDEX idx_conversation_type     ON conversation (type, last_message_at DESC);

CREATE TABLE chat_message (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversation(id),
    sender_id       UUID NOT NULL REFERENCES app_user(id),
    content         TEXT NOT NULL,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_conversation ON chat_message (conversation_id, created_at);
-- Toi uu dem chua doc: chi index nhung tin chua doc
CREATE INDEX idx_chat_message_unread ON chat_message (conversation_id, sender_id) WHERE read_at IS NULL;
