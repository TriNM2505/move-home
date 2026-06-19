package vn.movehome.backend.driver.finance;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "WithdrawalRequest")
@Table(name = "withdrawal_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "bank_name_snapshot", nullable = false, length = 100)
    private String bankNameSnapshot;

    @Column(name = "bank_account_number", nullable = false, length = 20)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", nullable = false, length = 100)
    private String bankAccountHolder;

    @Column(length = 500)
    private String note;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "bank_txn_ref", length = 100)
    private String bankTxnRef;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Builder.Default
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey = UUID.randomUUID();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
