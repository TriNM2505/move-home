package vn.movehome.backend.chat.dto;

import vn.movehome.backend.chat.ChatMessage;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO tra ve cho 1 tin nhan.
 * mine: tin nay do nguoi dang xem gui hay khong (server tinh cho REST; client tu tinh lai cho WS
 * bang cach so sanh senderId voi userId cua minh).
 * imageUrl: signed URL cua anh dinh kem (NULL neu tin chi co text). Chi thanh vien hoi thoai lay duoc.
 */
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        boolean mine,
        String content,
        String imageUrl,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {
    public static ChatMessageResponse of(ChatMessage m, String senderName, boolean mine, String imageUrl) {
        return new ChatMessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                senderName,
                mine,
                m.getContent(),
                imageUrl,
                m.getCreatedAt(),
                m.getReadAt());
    }
}
