package vn.movehome.backend.blog.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 1 bai dang tren feed cong dong.
 * HR-17: endpoint PUBLIC chi tra du lieu hien thi cong khai — ten + avatar tac gia,
 * noi dung, rating, anh. KHONG tra email/phone/PII khac.
 * createdAt: ISO 8601 UTC (AC-07), FE convert sang Asia/Ho_Chi_Minh khi hien thi.
 */
public record BlogPostResponse(
        UUID id,
        String authorName,
        String authorAvatarUrl,
        String content,
        Integer rating,
        List<String> photos,
        long commentCount,
        Instant createdAt) {
}
