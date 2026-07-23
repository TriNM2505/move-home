-- =============================================================================
-- Bang: order_cancellation_refund  (26/32)
-- Tac dung: Yeu cau HOAN COC khi Khach chu dong huy don luc CHUA co tai xe nhan
--           (don o CONFIRMED, driver_id NULL). Khach nhap ly do + dinh kem anh;
--           Manager duyet thu cong -> hoan coc 30% ve customer_wallet, hoac tu
--           choi. Moi don toi da 1 yeu cau (order_id UNIQUE).
-- Nguon migration: V41.
-- Constitution: HR-14 (chinh sach hoan coc), AC-08 (NUMERIC(15,0)), AC-07,
--               AC-13 (refund ghi transaction o tang service), AC-14 (VARCHAR+CHECK).
-- Phu thuoc: service_order, app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE order_cancellation_refund (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id         UUID          NOT NULL REFERENCES service_order(id),
    customer_id      UUID          NOT NULL REFERENCES app_user(id),
    reason           VARCHAR(500)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'REFUNDED', 'REJECTED')),
    refund_amount    NUMERIC(15,0) CHECK (refund_amount IS NULL OR refund_amount >= 0),   -- = coc 30% khi REFUNDED
    rejection_reason VARCHAR(500),
    processed_by     UUID          REFERENCES app_user(id),
    processed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_cancellation_refund     PRIMARY KEY (id),
    CONSTRAINT uq_order_cancellation_refund_order UNIQUE (order_id),
    CONSTRAINT ck_order_cancellation_refund_terminal CHECK (
        (status = 'PENDING'  AND processed_by IS NULL     AND processed_at IS NULL
                             AND refund_amount IS NULL      AND rejection_reason IS NULL)
     OR (status = 'REFUNDED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                             AND refund_amount IS NOT NULL AND refund_amount > 0 AND rejection_reason IS NULL)
     OR (status = 'REJECTED' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
                             AND rejection_reason IS NOT NULL AND refund_amount IS NULL)
    )
);

CREATE INDEX idx_order_cancellation_refund_pending  ON order_cancellation_refund (created_at ASC, id ASC) WHERE status = 'PENDING';
CREATE INDEX idx_order_cancellation_refund_customer ON order_cancellation_refund (customer_id, created_at DESC, id DESC);

CREATE TRIGGER trg_order_cancellation_refund_updated_at
    BEFORE UPDATE ON order_cancellation_refund
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  order_cancellation_refund              IS 'Hoan coc khi khach huy don luc chua co tai xe (CONFIRMED). Manager duyet -> refund ve customer_wallet (HR-14).';
COMMENT ON COLUMN order_cancellation_refund.refund_amount IS 'So tien hoan (coc 30% = total_quote * commission_rate_snapshot floor) khi REFUNDED.';
