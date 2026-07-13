package vn.movehome.backend.order;

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

import java.time.OffsetDateTime;
import java.util.UUID;

/** Anh bang chung khach dinh kem khi huy don, anh xa bang order_cancellation_photo (Flyway V41). AC-10. */
@Entity(name = "OrderCancellationPhoto")
@Table(name = "order_cancellation_photo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cancellation_id", nullable = false)
    private UUID cancellationId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;
}
