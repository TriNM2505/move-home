package vn.movehome.backend.customer.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerWithdrawalRequestRepository extends JpaRepository<CustomerWithdrawalRequest, UUID> {

    // Khoa cac yeu cau PENDING cua 1 khach de tinh so tien dang giu cho khi tao yeu cau moi.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from CustomerWithdrawalRequest r
            where r.customerId = :customerId
              and r.status = 'PENDING'
            """)
    List<CustomerWithdrawalRequest> findPendingByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from CustomerWithdrawalRequest r
            where r.id = :id
            """)
    Optional<CustomerWithdrawalRequest> findByIdForUpdate(@Param("id") UUID id);

    // Hang doi PENDING cho Admin duyet (FIFO).
    Page<CustomerWithdrawalRequest> findByStatus(String status, Pageable pageable);

    // Lich su rut tien cua 1 khach hang.
    Page<CustomerWithdrawalRequest> findByCustomerId(UUID customerId, Pageable pageable);

    boolean existsByBankTxnRef(String bankTxnRef);

    @Query("""
            select count(r)
            from CustomerWithdrawalRequest r
            where r.status = 'PENDING'
            """)
    long countPending();

    @Query("""
            select coalesce(sum(r.amount), 0)
            from CustomerWithdrawalRequest r
            where r.status = 'PENDING'
            """)
    BigDecimal sumPendingAmount();

    @Query("""
            select r
            from CustomerWithdrawalRequest r
            where r.status = 'PENDING'
            order by r.requestedAt asc, r.id asc
            """)
    List<CustomerWithdrawalRequest> findOldestPending(Pageable pageable);

    @Query("""
            select count(r)
            from CustomerWithdrawalRequest r
            where r.status = 'PENDING'
              and r.requestedAt < :threshold
            """)
    long countPendingRequestedBefore(@Param("threshold") OffsetDateTime threshold);
}
