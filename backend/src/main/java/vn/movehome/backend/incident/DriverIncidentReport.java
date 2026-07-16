package vn.movehome.backend.incident;

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
 * Bao su co tai xe giua chuyen (hong xe / ly do bat ngo khong the giao).
 * Anh xa bang driver_incident_report (Flyway V44).
 * AC-14: status la String (VARCHAR + CHECK, khong dung enum @Enumerated).
 * created_at/updated_at do DB default + trigger quan ly (insertable/updatable = false).
 */
@Entity(name = "DriverIncidentReport")
@Table(name = "driver_incident_report")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverIncidentReport {

    // Tai xe vua bao, cho Manager xac nhan.
    public static final String STATUS_PENDING = "PENDING";
    // Manager xac nhan su co, don da ban lai pool CONFIRMED.
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    // Da co tai xe khac nhan lai don.
    public static final String STATUS_RESOLVED_REASSIGNED = "RESOLVED_REASSIGNED";
    // Qua 15 phut khong ai nhan -> hoan coc + boi thuong.
    public static final String STATUS_COMPENSATED = "COMPENSATED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "order_status_snapshot", nullable = false, length = 30)
    private String orderStatusSnapshot;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_PENDING;

    @Column(name = "reassign_deadline")
    private OffsetDateTime reassignDeadline;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "refund_amount", precision = 15, scale = 0)
    private BigDecimal refundAmount;

    @Column(name = "penalty_amount", precision = 15, scale = 0)
    private BigDecimal penaltyAmount;

    @Column(name = "compensated_by")
    private UUID compensatedBy;

    @Column(name = "compensated_at")
    private OffsetDateTime compensatedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
