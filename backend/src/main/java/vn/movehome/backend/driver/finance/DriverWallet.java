package vn.movehome.backend.driver.finance;

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

@Entity(name = "DriverWallet")
@Table(name = "driver_wallet")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false, unique = true)
    private UUID driverId;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_earned", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalEarned = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_withdrawn", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalWithdrawn = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
