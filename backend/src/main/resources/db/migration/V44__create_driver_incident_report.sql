-- =============================================================================
-- V44: Bao su co tai xe giua chuyen (hong xe / ly do bat ngo khong the giao).
--
-- Luong: tai xe dang o ACCEPTED (tren duong / da den diem don) hoac IN_PROGRESS
--   (dang giao) bam "Bao su co" + mo ta + toi da 3 anh -> Manager xac nhan
--   -> don ban lai pool CONFIRMED (driver_id = NULL) cho tai xe khac nhan.
--   Neu qua 15 phut khong ai nhan -> Manager duoc chon hoan coc 30% + boi thuong
--   200.000d cho khach (ca hai ve customer_wallet); rieng 200.000d tru vao tai xe
--   gay su co (cong ty chiu phan coc). Quyet dinh leader 2026-07-16.
--
-- Tuan thu: AC-07 TIMESTAMPTZ | AC-08 NUMERIC(15,0) | AC-14 VARCHAR + CHECK (khong ENUM)
--           HR-21 khong dung reserved word | AC-13 tien ghi transaction (o tang service)
-- Mirror cau truc V41 (order_cancellation_refund + order_cancellation_photo).
-- =============================================================================

-- 1) Bao cao su co cua tai xe.
CREATE TABLE driver_incident_report (

    id                     UUID            NOT NULL DEFAULT gen_random_uuid(),

    order_id               UUID            NOT NULL
                           REFERENCES service_order(id),
    driver_id              UUID            NOT NULL
                           REFERENCES app_user(id),

    -- Mo ta loi tai xe nhap (bat buoc, tang service validate <= 500 ky tu).
    reason                 VARCHAR(500)    NOT NULL,

    -- Trang thai don TAI THOI DIEM bao su co (ACCEPTED | IN_PROGRESS) — de audit.
    order_status_snapshot  VARCHAR(30)     NOT NULL
                           CHECK (order_status_snapshot IN ('ACCEPTED', 'IN_PROGRESS')),

    status                 VARCHAR(30)     NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN (
                               'PENDING',              -- tai xe vua bao, cho Manager xac nhan
                               'CONFIRMED',            -- Manager xac nhan, don da ban lai pool
                               'RESOLVED_REASSIGNED',  -- da co tai xe khac nhan lai
                               'COMPENSATED'           -- qua 15p khong ai nhan -> hoan coc + boi thuong
                           )),

    -- Han 15 phut de tai xe khac nhan lai; set khi Manager CONFIRMED, NULL khi PENDING.
    reassign_deadline      TIMESTAMPTZ,

    -- Manager xac nhan su co.
    confirmed_by           UUID            REFERENCES app_user(id),
    confirmed_at           TIMESTAMPTZ,

    -- Khi COMPENSATED: refund_amount = coc 30% + 200.000 (ve customer_wallet);
    --   penalty_amount  = 200.000 (tru vao vi tai xe gay su co).
    refund_amount          NUMERIC(15,0)   CHECK (refund_amount IS NULL OR refund_amount >= 0),
    penalty_amount         NUMERIC(15,0)   CHECK (penalty_amount IS NULL OR penalty_amount >= 0),
    compensated_by         UUID            REFERENCES app_user(id),
    compensated_at         TIMESTAMPTZ,

    created_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_incident_report PRIMARY KEY (id)
);

-- Toi da 1 su co DANG MO (PENDING/CONFIRMED) tren 1 don tai 1 thoi diem.
-- Cho phep nhieu su co theo vong doi don (neu tai xe moi nhan lai roi lai hong).
CREATE UNIQUE INDEX uq_driver_incident_open_per_order
    ON driver_incident_report (order_id)
    WHERE status IN ('PENDING', 'CONFIRMED');

-- Hang doi PENDING cho Manager xac nhan theo FIFO.
CREATE INDEX idx_driver_incident_pending
    ON driver_incident_report (created_at ASC, id ASC)
    WHERE status = 'PENDING';

-- Cac su co da CONFIRMED, sap xep theo han reassign de Manager theo doi moc 15 phut.
CREATE INDEX idx_driver_incident_confirmed
    ON driver_incident_report (reassign_deadline ASC, id ASC)
    WHERE status = 'CONFIRMED';

-- Lich su theo tai xe (moi nhat truoc).
CREATE INDEX idx_driver_incident_driver
    ON driver_incident_report (driver_id, created_at DESC, id DESC);

-- Trigger tu dong cap nhat updated_at (dung lai ham tao o V1).
CREATE TRIGGER trg_driver_incident_report_updated_at
    BEFORE UPDATE ON driver_incident_report
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- 2) Anh bang chung tai xe dinh kem khi bao su co (toi da 3 anh/su co — enforce o service).
-- Luu Cloudinary signed upload theo AC-10 (khong luu BLOB), giong order_cancellation_photo (V41).
CREATE TABLE driver_incident_photo (

    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),

    incident_id           UUID            NOT NULL
                          REFERENCES driver_incident_report(id),

    -- secure_url Cloudinary (resource_type=authenticated); hien thi qua signed URL.
    url                   VARCHAR(500)    NOT NULL,
    -- public_id de ky URL khi Manager xem + de xoa asset khi can.
    public_id             VARCHAR(255)    NOT NULL,

    uploaded_by_user_id   UUID            REFERENCES app_user(id),
    uploaded_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_incident_photo PRIMARY KEY (id)
);

CREATE INDEX idx_driver_incident_photo_incident
    ON driver_incident_photo (incident_id, uploaded_at);


COMMENT ON TABLE driver_incident_report IS 'Bao su co tai xe giua chuyen (ACCEPTED/IN_PROGRESS). Manager xac nhan -> ban don lai pool; qua 15p khong ai nhan -> hoan coc + boi thuong 200k (200k tru vao tai xe).';
COMMENT ON COLUMN driver_incident_report.refund_amount IS 'So tien hoan cho khach khi COMPENSATED = coc 30% + boi thuong 200.000 (ve customer_wallet).';
COMMENT ON COLUMN driver_incident_report.penalty_amount IS 'So tien tru vao vi tai xe gay su co khi COMPENSATED (200.000). Cong ty chiu phan coc.';
COMMENT ON TABLE driver_incident_photo IS 'Anh bang chung tai xe dinh kem khi bao su co; Cloudinary signed upload theo AC-10.';
