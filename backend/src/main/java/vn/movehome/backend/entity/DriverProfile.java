package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ho so bổ sung cua tai xe (1-1 voi User co role=DRIVER).
 * Luu thong tin xe, giay to, thong ke hieu suat.
 * Tao sau khi Driver hoan tat Step 3 (upload giay to) cua Driver Onboarding flow.
 */
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
    private UUID id;

    // FK 1-1 toi app_user.id (chi user co role=DRIVER) — unique dam bao 1 profile/driver
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    // So GPLX (Giay phep lai xe) — unique tren toan he thong
    @Column(name = "license_number", length = 50, unique = true)
    private String licenseNumber;

    // Hang GPLX: B2, C, D, E, F... (theo quy dinh Viet Nam)
    @Column(name = "license_class", length = 10)
    private String licenseClass;

    // Bien so xe (vd: 30A-12345) — lay tu VEHICLE_REGISTRATION document
    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    // Mo ta loai xe (vd: "Xe tai vua 1 tan", "Xe 3 gac 500kg") — display cho Manager
    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    // Tai trong toi da cua xe (kg) — thong tin tham khao, khong dung de validation cung
    @Column(name = "vehicle_capacity_kg")
    private Integer vehicleCapacityKg;

    // Tien coc hien tai cua Driver trong he thong — VND, BigDecimal scale=0 (AC-08)
    // Ban dau = 3.000.000 VND sau khi dong coc. Tru khi co DamageReport.
    @Column(name = "deposit_amount", precision = 15, scale = 0, nullable = false)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    // Thoi gian dong coc 3 trieu thanh cong (sau IPN VNPay confirm)
    @Column(name = "deposit_paid_at")
    private Instant depositPaidAt;

    // Thoi gian Manager duyet (approve) ho so Driver
    @Column(name = "approved_at")
    private Instant approvedAt;

    // Manager nao da duyet Driver nay — NULL neu chua duyet hoac REJECTED
    @Column(name = "approved_by_manager_id")
    private UUID approvedByManagerId;

    // Tong so don da hoan thanh thanh cong — cap nhat khi order → COMPLETED
    @Column(name = "total_orders_completed", nullable = false)
    @Builder.Default
    private Integer totalOrdersCompleted = 0;

    // Tong thu nhap da nhan (sau escrow 2h, truoc thue) — VND, BigDecimal scale=0 (AC-08)
    @Column(name = "total_revenue", precision = 15, scale = 0, nullable = false)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    // Diem danh gia trung binh (0.0 - 5.0) — cap nhat sau moi don Customer danh gia
    // Double du vi day la rating, khong phai tien (AC-08 chi ap dung cho money fields)
    @Column(name = "average_rating", nullable = false)
    @Builder.Default
    private  BigDecimal averageRating = new BigDecimal("0.00");

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
