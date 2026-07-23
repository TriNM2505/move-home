-- =============================================================================
-- Bang: chat_message  (23/32)
-- Tac dung: Tin nhan trong 1 hoi thoai. Luu ben vung song song voi day realtime
--           WebSocket. Ho tro 1 anh/tin (image_public_id, hien qua signed URL);
--           read_at danh dau da doc (toi uu dem chua doc).
-- Nguon migration: V36 (goc) + V38 (image_public_id).
-- Constitution: AC-05 (chat), AC-10 (anh Cloudinary signed), AC-07 (TIMESTAMPTZ).
-- =============================================================================

CREATE TABLE chat_message (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversation(id),
    sender_id       UUID        NOT NULL REFERENCES app_user(id),
    content         TEXT        NOT NULL,           -- tin anh: content='' + image_public_id co gia tri
    image_public_id TEXT,                           -- V38: Cloudinary public_id (signed URL)
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_conversation ON chat_message (conversation_id, created_at);
CREATE INDEX idx_chat_message_unread       ON chat_message (conversation_id, sender_id) WHERE read_at IS NULL;

COMMENT ON TABLE  chat_message                 IS 'Tin nhan chat (luu DB + day WebSocket). 1 anh/tin qua image_public_id.';
COMMENT ON COLUMN chat_message.image_public_id IS 'Cloudinary public_id (authenticated) — hien qua signed URL (AC-10). NULL = tin text.';
