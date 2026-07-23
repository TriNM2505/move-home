-- =============================================================================
-- Bang: commission_settings  (20/32)
-- Tac dung: Cau hinh gia/hoa hong ACTIVE dang SINGLETON (chi 1 dong, id=1). Giu
--           ty le commission, phu thu (peak/alley/floor JSONB), don gia/km theo
--           loai xe, phi boc xep, coc tai xe, muc rut toi thieu. Co optimistic
--           version; don cu giu commission_rate_snapshot rieng nen khong bi anh huong.
-- Nguon migration: V16.
-- Constitution: AC-08 (NUMERIC(15,0)/(5,4)), AC-07 (TIMESTAMPTZ). Spec #014.
-- =============================================================================

CREATE TABLE commission_settings (
    id                    INTEGER       NOT NULL DEFAULT 1 CHECK (id = 1),   -- singleton
    commission_rate       NUMERIC(5,4)  NOT NULL DEFAULT 0.3000 CHECK (commission_rate BETWEEN 0.0500 AND 0.5000),
    peak_surcharge_rate   NUMERIC(5,4)  NOT NULL DEFAULT 0.3000 CHECK (peak_surcharge_rate BETWEEN 0.0000 AND 1.0000),
    peak_hours            JSONB         NOT NULL DEFAULT '[{"start":"07:00","end":"09:00"},{"start":"17:00","end":"19:00"}]',
    alley_surcharge_rate  NUMERIC(5,4)  NOT NULL DEFAULT 0.2000 CHECK (alley_surcharge_rate BETWEEN 0.0000 AND 1.0000),
    floor_surcharge_tiers JSONB         NOT NULL DEFAULT '[{"min_floor":2,"max_floor":3,"rate":0.2000},{"min_floor":4,"max_floor":5,"rate":0.3000},{"min_floor":6,"max_floor":30,"rate":0.5000}]',
    base_rate_per_km      JSONB         NOT NULL DEFAULT '{"TRUCK_500KG":20000,"TRUCK_1T":30000,"TRUCK_15T":40000}',
    porter_fee_per_person JSONB         NOT NULL DEFAULT '{"TRUCK_500KG":150000,"TRUCK_1T":200000,"TRUCK_15T":300000}',
    driver_deposit_vnd    NUMERIC(15,0) NOT NULL DEFAULT 3000000 CHECK (driver_deposit_vnd BETWEEN 0 AND 50000000),
    min_withdrawal_vnd    NUMERIC(15,0) NOT NULL DEFAULT 100000  CHECK (min_withdrawal_vnd BETWEEN 50000 AND 1000000),
    version               BIGINT        NOT NULL DEFAULT 1 CHECK (version > 0),
    last_updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_updated_by       UUID          REFERENCES app_user(id),

    CONSTRAINT pk_commission_settings PRIMARY KEY (id)
);

-- Seed dong cau hinh mac dinh (id=1)
INSERT INTO commission_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE  commission_settings                 IS 'Cau hinh gia/hoa hong active singleton (id=1) co optimistic version (spec #014).';
COMMENT ON COLUMN commission_settings.commission_rate IS 'Ty le hoa hong cho quote/order MOI; order cu giu commission_rate_snapshot.';
