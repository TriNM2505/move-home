package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Don hang chuyen nha — thuc the trung tam cua he thong Move_home.
 * Bang ten "service_order" (KHONG dung "order" — reserved word PostgreSQL).
 * Constitution AC-09: soft delete qua deleted_at.
 * AC-07: tat ca timestamp la TIMESTAMP WITH TIME ZONE, luu UTC.
 * AC-08: totalQuote, commissionRateSnapshot dung BigDecimal (khong float/double).
 *
 * Luu y: ten truong totalQuote va commissionRateSnapshot la bat buoc cho
 * Admin Dashboard FR-006 (commission = totalQuote * commissionRateSnapshot).
 */
@Entity(name = "ServiceOrder")  // Ten JPQL la "ServiceOrder" tranh nham voi tu khoa ORDER BY
@Table(
    name = "service_order",
    indexes = {
        @Index(name = "idx_order_code",     columnList = "order_code",         unique = true),
        @Index(name = "idx_order_customer", columnList = "customer_id,created_at"),
        @Index(name = "idx_order_driver",   columnList = "driver_id,created_at"),
        @Index(name = "idx_order_status",   columnList = "status,created_at")
    }
)
@SQLDelete(sql = "UPDATE service_order SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Ma don hang duy nhat, hien thi cho Customer/Driver (vd: "MH202606010001")
    @Column(name = "order_code", nullable = false, unique = true, length = 20)
    private String orderCode;

    // Customer dat don — FK toi app_user.id
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    // Driver duoc phan cong — NULL khi don o trang thai PENDING (chua co Driver)
    @Column(name = "driver_id")
    private UUID driverId;

    // Dia chi va quan noi thanh Ha Noi
    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "pickup_district", length = 100)
    private String pickupDistrict;

    @Column(name = "dropoff_address", nullable = false, length = 500)
    private String dropoffAddress;

    @Column(name = "dropoff_district", length = 100)
    private String dropoffDistrict;

    // Gio khach muon bat dau chuyen (dung tinh peak-hour surcharge — AC-07)
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    // Trang thai vong doi don hang
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    // Tong bao gia da chot — VND nguyen dong, BigDecimal scale=0 (AC-08)
    // Base price + peak surcharge + alley surcharge + floor surcharge + porter fee
    @Column(name = "total_quote", precision = 15, scale = 0, nullable = false)
    private BigDecimal totalQuote;

    // Ty le commission cua cong ty tai thoi diem tao don (SNAPSHOT — khong thay doi sau)
    // Mac dinh 30% = 0.3000. Admin co the thay doi rate nhung don cu giu rate cu.
    // Bat buoc cho Admin Dashboard FR-006: commission = total_quote * commission_rate_snapshot
    @Column(name = "commission_rate_snapshot", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal commissionRateSnapshot = new BigDecimal("0.3000");

    // Khoang cach tinh tu OSRM API (km, co the null neu dung fallback bang quan→quan)
    // NUMERIC(6,2) trong DB → phai dung BigDecimal, khong dung Double (AC-08 pattern)
    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    // Uoc tinh thoi gian chuyen (phut) — thong tin tham khao cho Driver/Customer
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    // Set khi order → COMPLETED; dung tinh escrow 2h (CONTEXT.md §2) va dashboard FR-006
    @Column(name = "completed_at")
    private Instant completedAt;

    // Set khi order → CANCELLED; luu kem ly do huy
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // Ly do huy: nhap boi Manager hoac tu dong boi system (timeout 15 phut — HR-09)
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    // Ghi chu cua Customer cho Driver (vd: "Chung cu lau 5, khong co thang may")
    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Soft delete — AC-09. NULL = con hieu luc; co gia tri = da huy/xoa logic
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
