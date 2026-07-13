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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yeu cau hoan coc khi khach chu dong huy don luc chua co tai xe nhan (CONFIRMED).
 * Anh xa bang order_cancellation_refund (Flyway V41).
 * AC-14: status la String (VARCHAR + CHECK, khong dung enum @Enumerated).
 * created_at/updated_at do DB default + trigger quan ly (insertable/updatable = false).
 */
@Entity(name = "OrderCancellationRefund")
@Table(name = "order_cancellation_refund")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationRefund {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "refund_amount", precision = 15, scale = 0)
    private BigDecimal refundAmount;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
