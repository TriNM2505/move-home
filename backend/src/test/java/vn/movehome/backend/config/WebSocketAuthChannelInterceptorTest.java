package vn.movehome.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import vn.movehome.backend.security.JwtTokenProvider;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiem tra WebSocketAuthChannelInterceptor: xac thuc JWT tai buoc STOMP CONNECT
 * va gan Principal (name = userId) khi token hop le.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MessageChannel channel;

    @Test
    void connectWithValidBearerTokenAssignsPrincipalNamedAfterUserId() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.validateAccessToken("valid-token")).thenReturn(Optional.of(userId));

        Message<?> message = connectMessageWithAuthorizationHeader("Bearer valid-token");

        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    void connectWithInvalidBearerTokenDoesNotAssignPrincipal() {
        when(jwtTokenProvider.validateAccessToken("invalid-token")).thenReturn(Optional.empty());

        Message<?> message = connectMessageWithAuthorizationHeader("Bearer invalid-token");

        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNull();
    }

    @Test
    void connectWithoutAuthorizationHeaderDoesNotAssignPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNull();
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void connectWithAuthorizationHeaderNotUsingBearerPrefixDoesNotAssignPrincipal() {
        Message<?> message = connectMessageWithAuthorizationHeader("Basic dXNlcjpwYXNz");

        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNull();
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void nonConnectStompFrameIsPassedThroughWithoutTouchingToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void messageWithoutStompHeaderAccessorIsReturnedUnchanged() {
        Message<String> message = new GenericMessage<>("payload");

        WebSocketAuthChannelInterceptor interceptor = new WebSocketAuthChannelInterceptor(jwtTokenProvider);
        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtTokenProvider);
    }

    private Message<byte[]> connectMessageWithAuthorizationHeader(String headerValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", headerValue);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
