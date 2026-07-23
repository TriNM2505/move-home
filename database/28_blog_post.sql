-- =============================================================================
-- Bang: blog_post  (28/32)
-- Tac dung: Bai dang BLOG CONG DONG (Community Wall) — Khach dang review + anh ve
--           dich vu, rating tuy chon. Guest xem feed (chi doc), Customer dang.
--           Kiem duyet qua status VISIBLE/HIDDEN; soft delete.
-- Nguon migration: V42.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-09 (soft delete), AC-14 (VARCHAR+CHECK),
--               AC-10 (anh Cloudinary), HR-21 (khong reserved word). Khong lien quan tien.
-- Phu thuoc: app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE blog_post (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    author_id  UUID        NOT NULL REFERENCES app_user(id),
    content    TEXT        NOT NULL,
    rating     SMALLINT    CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),   -- tuy chon
    status     VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,                       -- soft delete (AC-09)

    CONSTRAINT pk_blog_post PRIMARY KEY (id)
);

CREATE INDEX idx_blog_post_feed   ON blog_post (created_at DESC, id DESC) WHERE status = 'VISIBLE' AND deleted_at IS NULL;
CREATE INDEX idx_blog_post_author ON blog_post (author_id, created_at DESC);

CREATE TRIGGER trg_blog_post_updated_at
    BEFORE UPDATE ON blog_post
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE blog_post IS 'Bai dang cong dong (review + anh) cua Customer; Guest xem feed. Kiem duyet VISIBLE/HIDDEN.';
