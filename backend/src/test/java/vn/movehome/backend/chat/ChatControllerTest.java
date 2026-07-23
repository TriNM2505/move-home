package vn.movehome.backend.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.chat.dto.ChatMessageResponse;
import vn.movehome.backend.chat.dto.ConversationResponse;
import vn.movehome.backend.chat.dto.DriverDirectoryItem;
import vn.movehome.backend.chat.dto.OpenConversationRequest;
import vn.movehome.backend.chat.dto.SendMessageRequest;
import vn.movehome.backend.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChatController controller = new ChatController(chatService);

    @Test
    void listConversationsDelegatesToServiceForAuthenticatedUser() {
        User me = User.builder().id(UUID.randomUUID()).build();
        ConversationResponse response = new ConversationResponse(
                UUID.randomUUID(), ConversationType.CUSTOMER_MANAGER, null, null,
                "Quản lý Move_home", "Xin chào", OffsetDateTime.now(), 0L);
        when(chatService.listConversations(me)).thenReturn(List.of(response));

        List<ConversationResponse> result = controller.listConversations(me);

        assertThat(result).containsExactly(response);
        verify(chatService).listConversations(me);
    }

    @Test
    void listConversationsThrowsUnauthorizedWhenPrincipalMissing() {
        assertThatThrownBy(() -> controller.listConversations(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason())
                            .isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }

    @Test
    void openConversationReturnsMatchingConversationFromRefreshedList() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        OpenConversationRequest request = new OpenConversationRequest(orderId, driverId, ConversationType.CUSTOMER_DRIVER);

        UUID convId = UUID.randomUUID();
        Conversation conv = Conversation.builder()
                .id(convId)
                .orderId(orderId)
                .type(ConversationType.CUSTOMER_DRIVER)
                .build();
        when(chatService.openConversation(me, orderId, driverId, ConversationType.CUSTOMER_DRIVER)).thenReturn(conv);

        ConversationResponse matching = new ConversationResponse(
                convId, ConversationType.CUSTOMER_DRIVER, orderId, "ORD-1",
                "Tài xế A", "Đã tới nơi", OffsetDateTime.now(), 2L);
        ConversationResponse other = new ConversationResponse(
                UUID.randomUUID(), ConversationType.CUSTOMER_MANAGER, null, null,
                "Quản lý Move_home", null, null, 0L);
        when(chatService.listConversations(me)).thenReturn(List.of(other, matching));

        ConversationResponse result = controller.openConversation(me, request);

        assertThat(result).isEqualTo(matching);
    }

    @Test
    void openConversationFallsBackToDefaultResponseWhenNotFoundInList() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID orderId = UUID.randomUUID();
        OpenConversationRequest request = new OpenConversationRequest(orderId, null, ConversationType.MANAGER_DRIVER);

        UUID convId = UUID.randomUUID();
        Conversation conv = Conversation.builder()
                .id(convId)
                .orderId(orderId)
                .type(ConversationType.MANAGER_DRIVER)
                .build();
        when(chatService.openConversation(me, orderId, null, ConversationType.MANAGER_DRIVER)).thenReturn(conv);
        when(chatService.listConversations(me)).thenReturn(List.of());

        ConversationResponse result = controller.openConversation(me, request);

        assertThat(result).isEqualTo(new ConversationResponse(
                convId, ConversationType.MANAGER_DRIVER, orderId, null, null, null, null, 0L));
    }

    @Test
    void messagesClampsPageAndSizeWithinBounds() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID convId = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        Page<ChatMessageResponse> page = mock(Page.class);
        when(chatService.getMessages(eq(me), eq(convId), any(Pageable.class))).thenReturn(page);

        Page<ChatMessageResponse> result = controller.messages(me, convId, -5, 0);

        assertThat(result).isSameAs(page);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatService).getMessages(eq(me), eq(convId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void messagesClampsSizeToMaxPageSize() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID convId = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        Page<ChatMessageResponse> page = mock(Page.class);
        when(chatService.getMessages(eq(me), eq(convId), any(Pageable.class))).thenReturn(page);

        controller.messages(me, convId, 2, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatService).getMessages(eq(me), eq(convId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void messagesUsesProvidedPageAndSizeWhenWithinBounds() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID convId = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        Page<ChatMessageResponse> page = mock(Page.class);
        when(chatService.getMessages(eq(me), eq(convId), any(Pageable.class))).thenReturn(page);

        controller.messages(me, convId, 1, 30);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatService).getMessages(eq(me), eq(convId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    void sendDelegatesContentToService() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID convId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("Xin chào bạn");
        ChatMessageResponse response = new ChatMessageResponse(
                UUID.randomUUID(), convId, me.getId(), "Khách", true, "Xin chào bạn", null,
                OffsetDateTime.now(), null);
        when(chatService.sendMessage(me, convId, "Xin chào bạn")).thenReturn(response);

        ChatMessageResponse result = controller.send(me, convId, request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void sendImageDelegatesFileToService() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID convId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "anh.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ChatMessageResponse response = new ChatMessageResponse(
                UUID.randomUUID(), convId, me.getId(), "Khách", true, null,
                "https://res.cloudinary.com/demo/anh.jpg", OffsetDateTime.now(), null);
        when(chatService.sendImage(me, convId, file)).thenReturn(response);

        ChatMessageResponse result = controller.sendImage(me, convId, file);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void markReadDelegatesToServiceAndReturnsSuccessFlag() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID convId = UUID.randomUUID();

        Map<String, Object> result = controller.markRead(me, convId);

        assertThat(result).isEqualTo(Map.of("success", true));
        verify(chatService).markRead(me, convId);
    }

    @Test
    void unreadCountReturnsCountFromService() {
        User me = User.builder().id(UUID.randomUUID()).build();
        when(chatService.unreadCount(me)).thenReturn(5L);

        Map<String, Long> result = controller.unreadCount(me);

        assertThat(result).isEqualTo(Map.of("unreadCount", 5L));
    }

    @Test
    void driverDirectoryRequiresAuthenticationAndDelegatesToService() {
        User me = User.builder().id(UUID.randomUUID()).build();
        List<DriverDirectoryItem> drivers = List.of(new DriverDirectoryItem(UUID.randomUUID(), "Tài xế A", "0900000000"));
        when(chatService.listActiveDrivers()).thenReturn(drivers);

        List<DriverDirectoryItem> result = controller.driverDirectory(me);

        assertThat(result).isEqualTo(drivers);
    }

    @Test
    void driverDirectoryThrowsUnauthorizedWhenPrincipalMissing() {
        assertThatThrownBy(() -> controller.driverDirectory(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
