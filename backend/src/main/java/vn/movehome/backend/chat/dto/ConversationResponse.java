package vn.movehome.backend.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO 1 dong trong danh sach hoi thoai.
 * counterpartName: ten ben doi thoai theo goc nhin cua nguoi dang xem (vd "Quan ly Move_home",
 * ten khach, hoac ten tai xe).
 */
public record ConversationResponse(
        UUID id,
        String type,
        UUID orderId,
        String orderCode,
        String counterpartName,
        String lastMessageText,
        OffsetDateTime lastMessageAt,
        long unreadCount
) {
}
