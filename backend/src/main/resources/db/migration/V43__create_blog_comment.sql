-- =============================================================================
-- V43: Binh luan Blog cong dong (Pha B). Customer binh luan duoi bai; Manager tra loi
-- (hien badge "Quan ly"). Chu bai nhan notification "da co phan hoi".
--
-- Tuan thu: AC-07 TIMESTAMPTZ | AC-09 soft delete | AC-14 VARCHAR + CHECK (khong ENUM)
--           HR-21 khong dung reserved word (blog_comment).
-- =============================================================================

CREATE TABLE blog_comment (

    id            UUID            NOT NULL DEFAULT gen_random_uuid(),

    post_id       UUID            NOT NULL
                  REFERENCES blog_post(id),

    author_id     UUID            NOT NULL
                  REFERENCES app_user(id),

    -- Snapshot vai tro nguoi binh luan de render badge (CUSTOMER / MANAGER) khong can join role.
    author_role   VARCHAR(20)     NOT NULL
                  CHECK (author_role IN ('CUSTOMER', 'MANAGER')),

    content       TEXT            NOT NULL,

    -- Kiem duyet (Pha C): VISIBLE / HIDDEN. Mac dinh VISIBLE.
    status        VARCHAR(20)     NOT NULL DEFAULT 'VISIBLE'
                  CHECK (status IN ('VISIBLE', 'HIDDEN')),

    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT pk_blog_comment PRIMARY KEY (id)
);

-- Liet ke binh luan cua 1 bai theo thu tu thoi gian (cu -> moi).
CREATE INDEX idx_blog_comment_post
    ON blog_comment (post_id, created_at ASC, id ASC)
    WHERE status = 'VISIBLE' AND deleted_at IS NULL;

CREATE TRIGGER trg_blog_comment_updated_at
    BEFORE UPDATE ON blog_comment
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE blog_comment IS 'Binh luan duoi bai Blog cong dong; Customer binh luan, Manager tra loi (author_role). Kiem duyet qua status VISIBLE/HIDDEN.';
