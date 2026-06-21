package vn.movehome.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho bang transaction (audit trail tien te).
 * Khong co soft delete — transaction la append-only log (Constitution AC-13).
 * KHONG bao gio delete hay update ban ghi — neu can revert: them ADJUSTMENT transaction moi.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

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
}
