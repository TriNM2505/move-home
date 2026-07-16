package vn.movehome.backend.blog.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 1 binh luan duoi bai Blog cong dong.
 * authorRole: "CUSTOMER" hoac "MANAGER" — FE render badge "Quan ly" khi la MANAGER.
 * HR-17: chi tra ten + avatar cong khai, KHONG tra PII (email/phone).
 */
public record BlogCommentResponse(
        UUID id,
        String authorName,
        String authorAvatarUrl,
        String authorRole,
        String content,
        Instant createdAt) {
}
