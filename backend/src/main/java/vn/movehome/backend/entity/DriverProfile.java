package vn.movehome.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ho so bo sung cua tai xe, anh xa dung bang driver_profile V4 + V10. */
@Entity
@Table(
        name = "driver_profile",
        indexes = {
                @Index(name = "idx_driver_profile_user_id", columnList = "user_id", unique = true),
                @Index(name = "idx_driver_profile_approved", columnList = "approved_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "license_number", length = 50, unique = true)
    private String licenseNumber;

    @Column(name = "license_class", length = 10)
    private String licenseClass;

    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(name = "vehicle_capacity_kg")
    private Integer vehicleCapacityKg;

    @Column(name = "deposit_amount", precision = 15, scale = 0, nullable = false)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "deposit_paid_at")
    private OffsetDateTime depositPaidAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by_manager_id")
    private UUID approvedByManagerId;

    @Column(name = "total_orders_completed", nullable = false)
    @Builder.Default
    private Integer totalOrdersCompleted = 0;

    @Column(name = "total_revenue", precision = 15, scale = 0, nullable = false)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    // Tài xế mới mặc định 5.00 sao cho đến khi có đánh giá đầu tiên (V40)
    @Column(name = "average_rating", precision = 3, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal averageRating = new BigDecimal("5.00");

    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt;

    @Column(name = "last_reminder_at")
    private OffsetDateTime lastReminderAt;

    @Version
    @Column(name = "profile_version", nullable = false)
    @Builder.Default
    private Long profileVersion = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
