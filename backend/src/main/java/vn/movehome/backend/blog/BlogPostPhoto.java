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

import java.time.Instant;
import java.util.UUID;

/**
 * Anh dinh kem 1 bai dang cong dong. Toi da 3 anh/bai (enforce o BlogPhotoService).
 * Cloudinary signed upload SERVER-SIDE theo AC-10; delivery type=upload (public) vi
 * day la noi dung cong khai — url dung truc tiep lam <img src>, khong can signed URL.
 * Bang "blog_post_photo" (HR-21). Migration V42.
 */
@Entity
@Table(name = "blog_post_photo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    // secure_url Cloudinary (public delivery).
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    // public_id de xoa asset khi go bai (Pha C cleanup).
    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}
