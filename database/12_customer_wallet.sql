-- =============================================================================
-- Bang: customer_wallet  (12/32)
-- Tac dung: Vi tong hop cua Khach hang (1-1). Giu so du kha dung (khong am) va
--           cac so lieu tong (da nap / da chi / da rut). Nhan tien hoan coc khi
--           huy don hoac boi thuong su co tai xe; co the rut ve ngan hang.
--           (Governance: pham vi vi Customer dang cho leader duyet — spec #021.)
-- Nguon migration: V8 (goc) + V39 (them total_withdrawn).
-- Constitution: AC-08 (NUMERIC(15,0)), HR-18 (balance >= 0), AC-07 (TIMESTAMPTZ).
-- Phu thuoc: ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE customer_wallet (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    customer_id     UUID          NOT NULL UNIQUE REFERENCES app_user(id),
    balance         NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),       -- HR-18
    total_topped_up NUMERIC(15,0) NOT NULL DEFAULT 0,
    total_spent     NUMERIC(15,0) NOT NULL DEFAULT 0,
    total_withdrawn NUMERIC(15,0) NOT NULL DEFAULT 0,                            -- V39
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_customer_wallet PRIMARY KEY (id)
);

CREATE TRIGGER trg_customer_wallet_updated_at
    BEFORE UPDATE ON customer_wallet
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  customer_wallet         IS 'Vi tong hop khach hang; giao dich chi tiet ghi append-only o bang transaction.';
COMMENT ON COLUMN customer_wallet.balance IS 'So du kha dung (VND, >=0) — HR-18.';
