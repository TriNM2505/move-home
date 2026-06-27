package vn.movehome.backend.dispute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "Dispute")
@Table(name = "dispute")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "claim_type", nullable = false, length = 30)
    private String claimType;

    @Column(name = "claim_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal claimAmount;

    @Column(name = "customer_statement", nullable = false, columnDefinition = "TEXT")
    private String customerStatement;

    @Column(name = "driver_response", columnDefinition = "TEXT")
    private String driverResponse;

    @Column(name = "driver_response_at")
    private OffsetDateTime driverResponseAt;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = DisputeStatus.OPEN;

    @Column(name = "resolution_amount", precision = 15, scale = 0)
    private BigDecimal resolutionAmount;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(nullable = false)
    private OffsetDateTime deadline;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
