package vn.movehome.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import vn.movehome.backend.security.JwtTokenProvider;

/**
 * Xac thuc WebSocket tai buoc STOMP CONNECT (khong the dua vao filter HTTP vi SockJS/WS khac luong).
 * Doc "Authorization: Bearer <token>" tu native header cua frame CONNECT → validate JWT → gan Principal
 * (name = userId). Nho vay SimpMessagingTemplate.convertAndSendToUser(userId, ...) den dung nguoi.
 * Token khong hop le → khong gan Principal: ket noi khong nhan duoc tin nao (moi tin deu la user destination).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                jwtTokenProvider.validateAccessToken(token).ifPresent(userId ->
                        // Principal la functional interface (SAM = getName)
                        accessor.setUser(userId::toString));
            } else {
                log.debug("WebSocket CONNECT khong co Bearer token — ket noi an danh, khong nhan duoc tin.");
            }
        }
        return message;
    }
}
