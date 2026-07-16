-- =============================================================================
-- V42: Blog cong dong (Community Wall) — khach hang dang review + anh ve dich vu,
-- trao doi cong khai. Guest xem duoc feed (chi doc), Customer dang bai.
-- Binh luan + Manager tra loi lam o Pha B (chua tao bang o day).
--
-- Tuan thu: AC-07 TIMESTAMPTZ | AC-09 soft delete (deleted_at) | AC-14 VARCHAR + CHECK (khong ENUM)
--           HR-21 khong dung reserved word (blog_post, blog_post_photo) | AC-10 anh qua Cloudinary
--           (KHONG luu BLOB) — day la noi dung CONG KHAI nen luu type=upload (public URL).
-- Khong lien quan tien (AC-08 N/A).
-- =============================================================================

-- 1) Bai dang cong dong (review + noi dung + rating tuy chon).
CREATE TABLE blog_post (

    id            UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- Tac gia bai dang (Customer). FK app_user; xoa tac gia (soft delete) khong xoa bai.
    author_id     UUID            NOT NULL
                  REFERENCES app_user(id),

    -- Noi dung review/binh luan cong khai (tang service validate 1..1000 ky tu).
    content       TEXT            NOT NULL,

    -- Danh gia sao tuy chon (1-5). NULL khi khach chi dang bai/anh khong cham diem.
    rating        SMALLINT        CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),

    -- Kiem duyet: VISIBLE hien tren feed, HIDDEN bi Manager an (Pha C). Mac dinh VISIBLE.
    status        VARCHAR(20)     NOT NULL DEFAULT 'VISIBLE'
                  CHECK (status IN ('VISIBLE', 'HIDDEN')),

    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    -- Soft delete (AC-09) — NULL = chua xoa.
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT pk_blog_post PRIMARY KEY (id)
);

-- Feed cong khai: bai VISIBLE, chua xoa, moi nhat truoc.
CREATE INDEX idx_blog_post_feed
    ON blog_post (created_at DESC, id DESC)
    WHERE status = 'VISIBLE' AND deleted_at IS NULL;

-- Truy van theo tac gia (trang ca nhan sau nay).
CREATE INDEX idx_blog_post_author
    ON blog_post (author_id, created_at DESC);

-- Tu dong cap nhat updated_at (dung lai ham tao o V1).
CREATE TRIGGER trg_blog_post_updated_at
    BEFORE UPDATE ON blog_post
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- 2) Anh dinh kem bai dang (toi da 3 anh/bai — enforce o service).
-- Cloudinary type=upload (public delivery) vi feed la noi dung cong khai; van la
-- signed upload SERVER-SIDE theo AC-10 (khong lo API key phia client).
CREATE TABLE blog_post_photo (

    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),

    post_id               UUID            NOT NULL
                          REFERENCES blog_post(id),

    -- secure_url Cloudinary (public) — dung truc tiep lam <img src>.
    url                   VARCHAR(500)    NOT NULL,
    -- public_id de xoa asset khi go bai (Pha C cleanup).
    public_id             VARCHAR(255)    NOT NULL,

    uploaded_by_user_id   UUID            REFERENCES app_user(id),
    uploaded_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_blog_post_photo PRIMARY KEY (id)
);

CREATE INDEX idx_blog_post_photo_post
    ON blog_post_photo (post_id, uploaded_at);


COMMENT ON TABLE blog_post IS 'Bai dang cong dong (review + anh) cua Customer; Guest xem feed, Customer dang. Kiem duyet qua status VISIBLE/HIDDEN.';
COMMENT ON TABLE blog_post_photo IS 'Anh dinh kem bai dang cong dong; Cloudinary signed upload server-side, delivery public (type=upload) theo AC-10.';
