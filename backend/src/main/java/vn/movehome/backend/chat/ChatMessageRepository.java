package vn.movehome.backend.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    // So tin chua doc trong 1 hoi thoai doi voi nguoi dung hien tai (tin nguoi khac gui, chua doc)
    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID senderId);

    // Tong so tin chua doc tren nhieu hoi thoai (badge tren nav)
    @Query("""
            select count(m) from ChatMessage m
            where m.readAt is null
              and m.senderId <> :userId
              and m.conversationId in :conversationIds
            """)
    long countUnreadInConversations(
            @Param("conversationIds") Collection<UUID> conversationIds,
            @Param("userId") UUID userId);

    // Danh dau da doc tat ca tin cua NGUOI KHAC trong hoi thoai
    @Modifying(clearAutomatically = true)
    @Query("""
            update ChatMessage m set m.readAt = :now
            where m.conversationId = :conversationId
              and m.senderId <> :userId
              and m.readAt is null
            """)
    int markConversationRead(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now);
}
