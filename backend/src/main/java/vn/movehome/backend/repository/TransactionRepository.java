package vn.movehome.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho bang transaction (audit trail tien te).
 * Khong co soft delete — transaction la append-only log (Constitution AC-13).
 * KHONG bao gio delete hay update ban ghi — neu can revert: them ADJUSTMENT transaction moi.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    interface TopEarnerProjection {
        UUID getDriverId();

        String getFullName();

        BigDecimal getTotalEarning();

        Long getCompletedOrders();
    }

    interface FinancialTrendProjection {
        String getBucket();

        BigDecimal getGrossBookingValue();

        BigDecimal getPlatformFee();
    }

    /**
     * Lich su giao dich cua mot user, moi nhat truoc (co phan trang).
     * Dung cho trang "Lich su vi" cua Driver hoac trang bao cao cua Admin.
     *
     * @param userId   ID cua user can xem lich su
     * @param pageable Spring Data paging (LIMIT + OFFSET)
     */
    List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Lịch sử giao dịch theo loại, dùng cho Driver earnings.
     */
    Page<Transaction> findByUserIdAndType(UUID userId, TransactionType type, Pageable pageable);

    /**
     * Idempotency cho earning: một đơn hàng chỉ được ghi DRIVER_EARNING một lần.
     */
    boolean existsByTypeAndRelatedOrderId(TransactionType type, UUID relatedOrderId);

    boolean existsByTypeAndRelatedWithdrawalId(TransactionType type, UUID relatedWithdrawalId);

    Optional<Transaction> findByTypeAndRelatedWithdrawalId(TransactionType type, UUID relatedWithdrawalId);

    /**
     * Tinh tong so tien cua mot user theo loai giao dich.
     * Dung de tinh so du vi: SUM(amount WHERE type=DRIVER_EARNING) - SUM(amount WHERE type=WITHDRAWAL).
     * Luu y: amount co the la negative (DAMAGE_DEDUCTION, REFUND) → COALESCE(SUM, 0).
     *
     * @param userId  ID cua user
     * @param type    Loai giao dich can tinh tong
     * @return Tong so tien, tra 0 neu khong co giao dich nao (COALESCE trong query)
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.userId = :userId AND t.type = :type")
    BigDecimal sumByUserAndType(@Param("userId") UUID userId, @Param("type") TransactionType type);

    /**
     * Kiem tra vnpay_txn_ref da duoc xu ly chua (idempotency — Constitution HR-15).
     * Dung truoc khi xu ly IPN VNPay de chong double-processing.
     */
    boolean existsByVnpayTxnRef(String vnpayTxnRef);

    /**
     * Lay ket qua da xu ly de IPN lap lai tra ve dung ban ghi cu, khong tao audit log moi.
     */
    Optional<Transaction> findByVnpayTxnRef(String vnpayTxnRef);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.type = :type
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumAmountByTypeBetween(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select t.userId as driverId, u.fullName as fullName,
                   coalesce(sum(t.amount), 0) as totalEarning,
                   count(t) as completedOrders
            from Transaction t
            join User u on u.id = t.userId
            where t.type = vn.movehome.backend.entity.TransactionType.DRIVER_EARNING
              and t.createdAt >= :from and t.createdAt < :to
            group by t.userId, u.fullName
            order by coalesce(sum(t.amount), 0) desc, t.userId asc
            """)
    List<TopEarnerProjection> findTopEarnersByDriverEarning(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query(value = """
            SELECT
                TO_CHAR(t.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS "bucket",
                COALESCE(SUM(CASE WHEN t.type = 'PLATFORM_FEE' THEN t.amount ELSE 0 END), 0) AS "grossBookingValue",
                COALESCE(SUM(CASE WHEN t.type = 'PLATFORM_FEE' THEN t.amount ELSE 0 END), 0) AS "platformFee"
            FROM transaction t
            WHERE t.created_at >= :from AND t.created_at < :to
            GROUP BY TO_CHAR(t.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD')
            ORDER BY "bucket"
            """, nativeQuery = true)
    List<FinancialTrendProjection> findFinancialTrendByType(
            @Param("from") Instant from,
            @Param("to") Instant to);
}
