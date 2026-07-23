-- =============================================================================
-- Bang: notification  (24/32)
-- Tac dung: Thong bao trong ung dung cho tung nguoi dung (chuong thong bao). Giu
--           loai, tieu de, noi dung va co doc chua. Bang toi gian (khong FK/CHECK/
--           index) — co chu y de nhe cho demo.
-- Nguon migration: V18.
-- Constitution: AC-07 (TIMESTAMPTZ). Spec #020.
-- =============================================================================

CREATE TABLE notification (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    type       VARCHAR(50) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE notification IS 'Thong bao trong ung dung theo tung user (chuong thong bao). Bang toi gian (spec #020).';
