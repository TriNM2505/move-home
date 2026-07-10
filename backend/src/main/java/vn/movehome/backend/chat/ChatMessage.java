package vn.movehome.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mot tin nhan trong hoi thoai. Append-only (khong sua/xoa noi dung).
 * read_at != NULL nghia la ben nhan da xem (mo hinh 2 ben / quay ho tro — chi ben khong-gui doc).
 * Constitution AC-07: timestamp TIMESTAMPTZ luu UTC.
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // Cloudinary public_id neu tin nhan kem 1 anh (AC-10, hien thi qua signed URL). NULL = tin chi co text.
    @Column(name = "image_public_id", columnDefinition = "TEXT")
    private String imagePublicId;

    // NULL = ben nhan chua doc
    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
