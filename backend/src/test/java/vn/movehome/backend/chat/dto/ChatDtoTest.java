package vn.movehome.backend.chat.dto;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.chat.ChatMessage;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cau truc (constructor + accessor) cho cac DTO record trong package chat.dto.
 * Cac record nay khong co logic nghiep vu — muc tieu la phu coverage instantiation/accessor
 * (Jacoco van tinh code sinh boi compiler cho record: constructor, equals/hashCode/toString, accessor).
 */
class ChatDtoTest {

    @Test
    void chatMessageResponseAccessorsExposeConstructorArguments() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime readAt = OffsetDateTime.now().plusMinutes(1);

        ChatMessageResponse response = new ChatMessageResponse(
                id, conversationId, senderId, "Tài xế A", true, "Xin chào",
                "https://res.cloudinary.com/demo/anh.jpg", createdAt, readAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.senderId()).isEqualTo(senderId);
        assertThat(response.senderName()).isEqualTo("Tài xế A");
        assertThat(response.mine()).isTrue();
        assertThat(response.content()).isEqualTo("Xin chào");
        assertThat(response.imageUrl()).isEqualTo("https://res.cloudinary.com/demo/anh.jpg");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.readAt()).isEqualTo(readAt);
        assertThat(response.toString()).contains("Xin chào");
        assertThat(response).isEqualTo(new ChatMessageResponse(
                id, conversationId, senderId, "Tài xế A", true, "Xin chào",
                "https://res.cloudinary.com/demo/anh.jpg", createdAt, readAt));
    }

    @Test
    void chatMessageResponseOfBuildsFromEntity() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime readAt = OffsetDateTime.now().plusMinutes(2);
        ChatMessage entity = ChatMessage.builder()
                .id(id)
                .conversationId(conversationId)
                .senderId(senderId)
                .content("Đã tới nơi")
                .imagePublicId("movehome/chat/abc")
                .readAt(readAt)
                .createdAt(createdAt)
                .build();

        ChatMessageResponse response = ChatMessageResponse.of(entity, "Quản lý Move_home", false, "https://signed-url");

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.senderId()).isEqualTo(senderId);
        assertThat(response.senderName()).isEqualTo("Quản lý Move_home");
        assertThat(response.mine()).isFalse();
        assertThat(response.content()).isEqualTo("Đã tới nơi");
        assertThat(response.imageUrl()).isEqualTo("https://signed-url");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.readAt()).isEqualTo(readAt);
    }

    @Test
    void conversationResponseAccessorsExposeConstructorArguments() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime lastMessageAt = OffsetDateTime.now();

        ConversationResponse response = new ConversationResponse(
                id, "CUSTOMER_DRIVER", orderId, "ORD-001", "Tài xế A", "Đã tới nơi", lastMessageAt, 3L);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.type()).isEqualTo("CUSTOMER_DRIVER");
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderCode()).isEqualTo("ORD-001");
        assertThat(response.counterpartName()).isEqualTo("Tài xế A");
        assertThat(response.lastMessageText()).isEqualTo("Đã tới nơi");
        assertThat(response.lastMessageAt()).isEqualTo(lastMessageAt);
        assertThat(response.unreadCount()).isEqualTo(3L);
        assertThat(response).isEqualTo(new ConversationResponse(
                id, "CUSTOMER_DRIVER", orderId, "ORD-001", "Tài xế A", "Đã tới nơi", lastMessageAt, 3L));
    }

    @Test
    void driverDirectoryItemAccessorsExposeConstructorArguments() {
        UUID id = UUID.randomUUID();

        DriverDirectoryItem item = new DriverDirectoryItem(id, "Tài xế B", "0912345678");

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.fullName()).isEqualTo("Tài xế B");
        assertThat(item.phone()).isEqualTo("0912345678");
        assertThat(item).isEqualTo(new DriverDirectoryItem(id, "Tài xế B", "0912345678"));
    }

    @Test
    void openConversationRequestAccessorsExposeConstructorArguments() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        OpenConversationRequest request = new OpenConversationRequest(orderId, driverId, "MANAGER_DRIVER");

        assertThat(request.orderId()).isEqualTo(orderId);
        assertThat(request.driverId()).isEqualTo(driverId);
        assertThat(request.type()).isEqualTo("MANAGER_DRIVER");
    }

    @Test
    void sendMessageRequestAccessorExposesContent() {
        SendMessageRequest request = new SendMessageRequest("Xin chào bạn");

        assertThat(request.content()).isEqualTo("Xin chào bạn");
    }
}
