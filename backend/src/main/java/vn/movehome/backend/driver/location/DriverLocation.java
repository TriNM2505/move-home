package vn.movehome.backend.driver.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_location")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocation {

    @Id
    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "current_order_id")
    private UUID currentOrderId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(precision = 5, scale = 2)
    private BigDecimal heading;

    @Column(name = "speed_kmh", precision = 6, scale = 2)
    private BigDecimal speedKmh;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
