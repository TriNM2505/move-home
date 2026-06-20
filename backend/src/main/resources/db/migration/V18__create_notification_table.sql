-- =============================================================================
-- V18: Tạo bảng thông báo trong ứng dụng.
-- Spec #006: Driver nhận thông báo phân công; Customer nhận thông báo khi trạng
-- thái workflow thay đổi. Lỗi gửi email/thông báo không rollback nghiệp vụ.
-- AC-07: created_at dùng TIMESTAMPTZ. HR-21: tên bảng không là reserved word.
-- =============================================================================

CREATE TABLE notification (

    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID            NOT NULL
                REFERENCES app_user(id),
    type        VARCHAR(50)     NOT NULL,
    title       VARCHAR(255)    NOT NULL,
    message     TEXT            NOT NULL,
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification PRIMARY KEY (id)
);


CREATE INDEX idx_notification_user_created
    ON notification (user_id, created_at DESC, id DESC);

CREATE INDEX idx_notification_user_unread
    ON notification (user_id, created_at DESC, id DESC)
    WHERE is_read = FALSE;


COMMENT ON TABLE notification IS 'Thông báo trong ứng dụng cho Customer, Driver, Manager và Admin.';
COMMENT ON COLUMN notification.user_id IS 'Người dùng nhận thông báo, tham chiếu app_user.id.';
COMMENT ON COLUMN notification.type IS 'Loại sự kiện thông báo do application định nghĩa.';
COMMENT ON COLUMN notification.is_read IS 'FALSE khi người dùng chưa đọc thông báo.';
COMMENT ON COLUMN notification.created_at IS 'Thời điểm tạo thông báo, lưu bằng TIMESTAMPTZ.';
