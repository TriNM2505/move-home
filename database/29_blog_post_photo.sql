-- =============================================================================
-- Bang: blog_post_photo  (29/32)
-- Tac dung: Anh dinh kem bai dang blog cong dong (toi da 3 anh/bai). Vi la noi
--           dung CONG KHAI nen luu Cloudinary type=upload (public delivery), dung
--           truc tiep lam <img src>; van la signed upload server-side (AC-10).
-- Nguon migration: V42.
-- Constitution: AC-10 (Cloudinary signed server-side), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE blog_post_photo (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    post_id             UUID         NOT NULL REFERENCES blog_post(id),
    url                 VARCHAR(500) NOT NULL,        -- secure_url public
    public_id           VARCHAR(255) NOT NULL,        -- de xoa asset khi go bai
    uploaded_by_user_id UUID         REFERENCES app_user(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_blog_post_photo PRIMARY KEY (id)
);

CREATE INDEX idx_blog_post_photo_post ON blog_post_photo (post_id, uploaded_at);

COMMENT ON TABLE blog_post_photo IS 'Anh dinh kem bai blog cong dong; Cloudinary signed upload, delivery public (AC-10).';
