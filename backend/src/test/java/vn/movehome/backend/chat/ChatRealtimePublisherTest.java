package vn.movehome.backend.chat;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import vn.movehome.backend.chat.dto.ChatMessageResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChatRealtimePublisherTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ChatRealtimePublisher publisher = new ChatRealtimePublisher(messagingTemplate);

    @Test
    void publishNewMessageSendsToEachRecipientUserDestination() {
        UUID recipient1 = UUID.randomUUID();
        UUID recipient2 = UUID.randomUUID();
        ChatMessageResponse payload = new ChatMessageResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Khách", false,
                "Xin chào", null, OffsetDateTime.now(), null);

        publisher.publishNewMessage(List.of(recipient1, recipient2), payload);

        verify(messagingTemplate).convertAndSendToUser(recipient1.toString(), "/queue/messages", payload);
        verify(messagingTemplate).convertAndSendToUser(recipient2.toString(), "/queue/messages", payload);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), eq("/queue/messages"), any());
    }

    @Test
    void publishNewMessageSwallowsExceptionFromMessagingTemplateForOneRecipientAndContinuesWithNext() {
        UUID failingRecipient = UUID.randomUUID();
        UUID okRecipient = UUID.randomUUID();
        ChatMessageResponse payload = new ChatMessageResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Khách", true,
                "Đã tới nơi", null, OffsetDateTime.now(), null);

        doThrow(new RuntimeException("socket closed"))
                .when(messagingTemplate)
                .convertAndSendToUser(eq(failingRecipient.toString()), eq("/queue/messages"), any());

        publisher.publishNewMessage(List.of(failingRecipient, okRecipient), payload);

        verify(messagingTemplate).convertAndSendToUser(failingRecipient.toString(), "/queue/messages", payload);
        verify(messagingTemplate).convertAndSendToUser(okRecipient.toString(), "/queue/messages", payload);
    }

    @Test
    void publishNewMessageWithEmptyRecipientsDoesNothing() {
        ChatMessageResponse payload = new ChatMessageResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Khách", true,
                "Hello", null, OffsetDateTime.now(), null);

        publisher.publishNewMessage(List.of(), payload);

        verify(messagingTemplate, times(0)).convertAndSendToUser(anyString(), anyString(), any());
    }
}
