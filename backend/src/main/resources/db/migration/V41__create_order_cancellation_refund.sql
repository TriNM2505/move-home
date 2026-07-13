-- =============================================================================
-- V41: Yeu cau hoan coc khi KHACH chu dong huy don luc CHUA co tai xe nhan
-- (don o trang thai CONFIRMED, driver_id = NULL). Khach nhap ly do + dinh kem
-- anh; Manager duyet thu cong roi bam "Hoan coc" -> tien coc 30% ve customer_wallet.
--
-- LUU Y CHINH SACH (override theo quyet dinh leader 2026-07-13):
--   CONTEXT §Huy don + HR-14 hien quy dinh khach huy tu CONFIRMED tro di la MAT coc
--   (chi COMPANY huy moi hoan). Luong nay CO Y di nguoc quy dinh do de than thien khach
--   khi chua tai xe nao cam ket. CONTEXT/constitution se duoc cap nhat sau (TODO leader).
--
-- Tuan thu: AC-07 TIMESTAMPTZ | AC-08 NUMERIC(15,0) | AC-14 VARCHAR + CHECK (khong ENUM)
--           HR-21 khong dung reserved word | AC-13 refund ghi transaction (o tang service)
-- =============================================================================

-- 1) Yeu cau hoan coc do huy don.
CREATE TABLE order_cancellation_refund (

    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),

    order_id           UUID            NOT NULL
                       REFERENCES service_order(id),
    customer_id        UUID            NOT NULL
                       REFERENCES app_user(id),

    -- Ly do khach nhap khi huy (bat buoc, tang service validate <= 500 ky tu).
    reason             VARCHAR(500)    NOT NULL,

    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'REFUNDED', 'REJECTED')),

    -- So tien da hoan (= coc 30%) khi REFUNDED; NULL khi PENDING/REJECTED.
    refund_amount      NUMERIC(15,0)   CHECK (refund_amount IS NULL OR refund_amount >= 0),
    rejection_reason   VARCHAR(500),

    processed_by       UUID            REFERENCES app_user(id),
    processed_at       TIMESTAMPTZ,

    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_cancellation_refund PRIMARY KEY (id),

    -- Moi don chi co toi da 1 yeu cau hoan coc (don chi huy 1 lan).
    CONSTRAINT uq_order_cancellation_refund_order UNIQUE (order_id),

    -- Toan ven field theo trang thai (giong ck_* cua withdrawal/dispute).
    CONSTRAINT ck_order_cancellation_refund_terminal CHECK (
        (
            status = 'PENDING'
            AND processed_by IS NULL
            AND processed_at IS NULL
            AND refund_amount IS NULL
            AND rejection_reason IS NULL
        )
        OR (
            status = 'REFUNDED'
            AND processed_by IS NOT NULL
            AND processed_at IS NOT NULL
            AND refund_amount IS NOT NULL
            AND refund_amount > 0
            AND rejection_reason IS NULL
        )
        OR (
            status = 'REJECTED'
            AND processed_by IS NOT NULL
            AND processed_at IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND refund_amount IS NULL
        )
    )
);

-- Hang doi PENDING cho Manager duyet theo FIFO.
CREATE INDEX idx_order_cancellation_refund_pending
    ON order_cancellation_refund (created_at ASC, id ASC)
    WHERE status = 'PENDING';

-- Lich su theo khach hang (moi nhat truoc).
CREATE INDEX idx_order_cancellation_refund_customer
    ON order_cancellation_refund (customer_id, created_at DESC, id DESC);

-- Trigger tu dong cap nhat updated_at (dung lai ham tao o V1).
CREATE TRIGGER trg_order_cancellation_refund_updated_at
    BEFORE UPDATE ON order_cancellation_refund
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- 2) Anh bang chung khach dinh kem khi huy (toi da 3 anh/yeu cau — enforce o service).
-- Luu Cloudinary signed upload theo AC-10 (khong luu BLOB), giong dispute_photo (V35).
CREATE TABLE order_cancellation_photo (

    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),

    cancellation_id       UUID            NOT NULL
                          REFERENCES order_cancellation_refund(id),

    -- secure_url Cloudinary (resource_type=authenticated); hien thi qua signed URL.
    url                   VARCHAR(500)    NOT NULL,
    -- public_id de ky URL khi Manager xem + de xoa asset khi can.
    public_id             VARCHAR(255)    NOT NULL,

    uploaded_by_user_id   UUID            REFERENCES app_user(id),
    uploaded_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_cancellation_photo PRIMARY KEY (id)
);

CREATE INDEX idx_order_cancellation_photo_cancellation
    ON order_cancellation_photo (cancellation_id, uploaded_at);


COMMENT ON TABLE order_cancellation_refund IS 'Yeu cau hoan coc khi khach huy don luc chua co tai xe (CONFIRMED). Manager duyet thu cong; refund ve customer_wallet.';
COMMENT ON COLUMN order_cancellation_refund.refund_amount IS 'So tien hoan (coc 30% = total_quote * commission_rate_snapshot floor) khi REFUNDED.';
COMMENT ON TABLE order_cancellation_photo IS 'Anh bang chung khach dinh kem khi huy don; Cloudinary signed upload theo AC-10.';
