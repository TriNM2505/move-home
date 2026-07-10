package vn.movehome.backend.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import vn.movehome.backend.chat.dto.ChatMessageResponse;

import java.util.Collection;
import java.util.UUID;

/**
 * Day tin nhan realtime qua WebSocket (STOMP) toi tung nguoi nhan cu the.
 * Chi dung "user destination" (/user/{id}/queue/messages) → moi user chi nhan tin cua rieng minh
 * (khong dung shared topic de tranh lo PII cho user khac — HR-17).
 * Client subscribe: "/user/queue/messages".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishNewMessage(Collection<UUID> recipientUserIds, ChatMessageResponse payload) {
        for (UUID userId : recipientUserIds) {
            try {
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", payload);
            } catch (Exception e) {
                // WebSocket loi khong duoc lam hong luong gui tin (tin da luu DB, client van poll/reload thay)
                log.warn("Khong day duoc tin nhan realtime toi user {}: {}", userId, e.getMessage());
            }
        }
    }
}
