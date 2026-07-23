-- =============================================================================
-- Bang: driver_wallet  (11/32)
-- Tac dung: Vi tong hop cua Tai xe (1-1). Giu so du kha dung (khong am), tong da
--           kiem va tong da rut. Cong tien khi don COMPLETED + het escrow 2h; tru
--           khi co DamageReport hoac khi rut. So cai chi tiet nam o bang transaction.
-- Nguon migration: V11.
-- Constitution: AC-08 (NUMERIC(15,0)), HR-18 (balance >= 0), AC-07 (TIMESTAMPTZ).
-- Phu thuoc: ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE driver_wallet (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    driver_id       UUID          NOT NULL UNIQUE REFERENCES app_user(id),
    balance         NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),        -- HR-18: khong am
    total_earned    NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (total_earned >= 0),
    total_withdrawn NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (total_withdrawn >= 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_wallet PRIMARY KEY (id)
);

CREATE TRIGGER trg_driver_wallet_updated_at
    BEFORE UPDATE ON driver_wallet
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE  driver_wallet         IS 'Vi tong hop tai xe; giao dich chi tiet ghi append-only o bang transaction.';
COMMENT ON COLUMN driver_wallet.balance IS 'So du kha dung (VND, >=0) — HR-18.';
