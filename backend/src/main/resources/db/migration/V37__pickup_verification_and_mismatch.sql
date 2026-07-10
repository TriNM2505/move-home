-- ============================================================
-- V37: Xac thuc tai xe TAI DIEM DON + khieu nai DRIVER_MISMATCH
-- - arrived_at: moc tai xe bam "Da den diem don" (don VAN o ACCEPTED, cho khach doi chieu).
--   Khach chi xac thuc/bao khong khop khi arrived_at != NULL.
-- - Noi CHECK claim_type de nhan them 'DRIVER_MISMATCH' (khieu nai TIEN-chuyen do khach bao sai tai xe/xe).
-- Constitution: AC-07 (TIMESTAMPTZ UTC), AC-14 (VARCHAR + CHECK, khong ENUM), AC-12 (Flyway).
-- ============================================================

ALTER TABLE service_order ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMPTZ;

-- CHECK claim_type goc la inline (Postgres tu dat ten dispute_claim_type_check). Drop roi tao lai co ten.
ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_claim_type_check;
ALTER TABLE dispute DROP CONSTRAINT IF EXISTS ck_dispute_claim_type;
ALTER TABLE dispute ADD CONSTRAINT ck_dispute_claim_type CHECK (claim_type IN (
    'DAMAGE',
    'MISSING_ITEM',
    'LATE_DELIVERY',
    'INAPPROPRIATE_BEHAVIOR',
    'OTHER',
    'DRIVER_MISMATCH'
));
