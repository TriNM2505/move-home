package vn.movehome.backend.blog;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Bai dang tren Blog cong dong (Community Wall) — Customer dang review + anh ve dich vu.
 * Bang "blog_post" (khong dung reserved word, HR-21).
 * AC-09: soft delete qua deleted_at. AC-07: timestamp TIMESTAMPTZ luu UTC.
 * AC-14: status luu String + CHECK o DB (khong dung enum type PostgreSQL).
 * Migration V42.
 */
@Entity
@Table(name = "blog_post")
@SQLDelete(sql = "UPDATE blog_post SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Tac gia (Customer). Luu id, khong FK entity de tranh N+1; join thu cong o service.
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // Danh gia sao tuy chon (1-5); NULL neu chi dang bai/anh khong cham diem.
    // Kieu Short de khop cot SMALLINT (int2) trong V42 (ddl-auto=validate, AC-12).
    @Column(name = "rating")
    private Short rating;

    // Kiem duyet: VISIBLE / HIDDEN (AC-14 — String, khong @Enumerated enum type)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "VISIBLE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Soft delete — NULL = chua xoa (AC-09)
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
