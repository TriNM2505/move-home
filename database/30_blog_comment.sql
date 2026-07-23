-- =============================================================================
-- Bang: blog_comment  (30/32)
-- Tac dung: Binh luan duoi bai blog cong dong. Customer binh luan; Manager tra
--           loi (author_role de render badge "Quan ly" khong can join). Kiem duyet
--           VISIBLE/HIDDEN; soft delete.
-- Nguon migration: V43.
-- Constitution: AC-07 (TIMESTAMPTZ), AC-09 (soft delete), AC-14 (VARCHAR+CHECK),
--               HR-21 (khong reserved word).
-- Phu thuoc: blog_post, app_user; ham update_updated_at_column() (01_app_user.sql).
-- =============================================================================

CREATE TABLE blog_comment (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id     UUID        NOT NULL REFERENCES blog_post(id),
    author_id   UUID        NOT NULL REFERENCES app_user(id),
    author_role VARCHAR(20) NOT NULL CHECK (author_role IN ('CUSTOMER', 'MANAGER')),   -- snapshot vai tro
    content     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT pk_blog_comment PRIMARY KEY (id)
);

CREATE INDEX idx_blog_comment_post ON blog_comment (post_id, created_at ASC, id ASC) WHERE status = 'VISIBLE' AND deleted_at IS NULL;

CREATE TRIGGER trg_blog_comment_updated_at
    BEFORE UPDATE ON blog_comment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE blog_comment IS 'Binh luan blog cong dong; Customer binh luan, Manager tra loi (author_role). Kiem duyet VISIBLE/HIDDEN.';
