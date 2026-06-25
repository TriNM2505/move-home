package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail moi luong tien trong he thong (Constitution AC-13).
 * Moi thay doi so du (coc Driver, thu nhap, phi nen tang, boi thuong) deu phai ghi o day.
 * KHONG UPDATE hoac DELETE ban ghi nay — append-only log (tuong tu wallet_transaction trong spec).
 *
 * Luu y: AC-08 — amount la BigDecimal scale=0. POSITIVE = tien vao, NEGATIVE = tien ra.
 * Vi du: Driver nhan tien → amount = +700_000; DamageReport tru coc → amount = -500_000.
 */
@Entity
@Table(
    name = "transaction",
    indexes = {
        @Index(name = "idx_transaction_user",   columnList = "user_id,created_at"),
        @Index(name = "idx_transaction_order",  columnList = "related_order_id"),
        @Index(name = "idx_transaction_vnpay",  columnList = "vnpay_txn_ref"),
        @Index(name = "idx_transaction_withdrawal", columnList = "related_withdrawal_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Chu the cua giao dich: Driver (nhan tien), Customer (thanh toan), System (platform fee)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Loai giao dich — xem TransactionType.java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    // So tien VND nguyen dong — POSITIVE = cong tien, NEGATIVE = tru tien (AC-08)
    // DB constraint: khong co gia tri NULL. Cho phep am (NEGATIVE khi tru).
    @Column(precision = 15, scale = 0, nullable = false)
    private BigDecimal amount;

    // Don hang lien quan — NULL neu giao dich khong lien quan den don cu the
    // (vd: DEPOSIT_TOP_UP cua Driver khi dang ky khong co don hang cu the)
    @Column(name = "related_order_id")
    private UUID relatedOrderId;

    @Column(name = "related_withdrawal_id")
    private UUID relatedWithdrawalId;

    @Column(name = "related_dispute_id")
    private UUID relatedDisputeId;

    @Column(name = "balance_after", precision = 15, scale = 0)
    private BigDecimal balanceAfter;

    // Mo ta ngan gon hien thi trong lich su giao dich
    @Column(length = 255)
    private String description;

    // Ma giao dich VNPay (vnp_TransactionNo) — unique khi co, NULL khi giao dich noi bo
    // Dung de idempotency check khi nhan IPN (Constitution HR-15)
    @Column(name = "vnpay_txn_ref", length = 100, unique = true)
    private String vnpayTxnRef;

    // Thoi gian tao — append-only, khong bao gio thay doi (AC-07)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
