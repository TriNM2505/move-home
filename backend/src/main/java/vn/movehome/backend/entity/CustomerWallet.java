package vn.movehome.backend.entity;

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

@Entity(name = "CustomerWallet")
@Table(name = "customer_wallet")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @Column(nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "total_topped_up", nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal totalToppedUp = BigDecimal.ZERO;

    @Column(name = "total_spent", nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    // Tong tien khach hang da rut thanh cong khoi vi (V39). Chi tang khi Admin PROCESSED.
    @Column(name = "total_withdrawn", nullable = false, precision = 15, scale = 0)
    @Builder.Default
    private BigDecimal totalWithdrawn = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
