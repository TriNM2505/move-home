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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "CustomerServiceOrder")
@Table(name = "service_order")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 20)
    private String orderCode;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "pickup_district", length = 100)
    private String pickupDistrict;

    @Column(name = "dropoff_address", nullable = false, length = 500)
    private String dropoffAddress;

    @Column(name = "dropoff_district", length = 100)
    private String dropoffDistrict;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING_PAYMENT";

    @Column(name = "total_quote", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalQuote;

    @Builder.Default
    @Column(name = "commission_rate_snapshot", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRateSnapshot = new BigDecimal("0.3000");

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    // Moc tai xe bam "Da den diem don" — don VAN o ACCEPTED, cho khach doi chieu tai xe/xe. NULL = chua den.
    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // Thoi diem khach tra not 70% (xac nhan boi VNPay IPN); NULL = chua tra
    @Column(name = "final_paid_at")
    private OffsetDateTime finalPaidAt;

    // Thoi diem escrow release 70% vao vi tai xe; NULL = chua release
    @Column(name = "earning_released_at")
    private OffsetDateTime earningReleasedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Builder.Default
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private String vehicleType = "TRUCK_500KG";

    @Builder.Default
    @Column(name = "porter_count", nullable = false)
    private Integer porterCount = 0;

    @Column(name = "pickup_lat", precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "dropoff_lat", precision = 10, scale = 7)
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng", precision = 10, scale = 7)
    private BigDecimal dropoffLng;

    @Column(name = "pickup_floor")
    private Integer pickupFloor;

    @Builder.Default
    @Column(name = "pickup_has_elevator", nullable = false)
    private Boolean pickupHasElevator = false;

    @Builder.Default
    @Column(name = "pickup_has_alley", nullable = false)
    private Boolean pickupHasAlley = false;

    @Column(name = "dropoff_floor")
    private Integer dropoffFloor;

    @Builder.Default
    @Column(name = "dropoff_has_elevator", nullable = false)
    private Boolean dropoffHasElevator = false;

    @Builder.Default
    @Column(name = "dropoff_has_alley", nullable = false)
    private Boolean dropoffHasAlley = false;

    @Column(name = "base_fare", precision = 15, scale = 0)
    private BigDecimal baseFare;

    @Builder.Default
    @Column(name = "peak_surcharge", precision = 15, scale = 0)
    private BigDecimal peakSurcharge = BigDecimal.ZERO.setScale(0);

    @Builder.Default
    @Column(name = "alley_surcharge", precision = 15, scale = 0)
    private BigDecimal alleySurcharge = BigDecimal.ZERO.setScale(0);

    @Builder.Default
    @Column(name = "floor_surcharge", precision = 15, scale = 0)
    private BigDecimal floorSurcharge = BigDecimal.ZERO.setScale(0);

    @Builder.Default
    @Column(name = "porter_fee", precision = 15, scale = 0)
    private BigDecimal porterFee = BigDecimal.ZERO.setScale(0);
}
