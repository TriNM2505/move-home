package vn.movehome.backend.driver.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from WithdrawalRequest r
            where r.driverId = :driverId
              and r.status = 'PENDING'
            """)
    List<WithdrawalRequest> findPendingByDriverIdForUpdate(@Param("driverId") UUID driverId);

    @Query("""
            select r
            from WithdrawalRequest r
            where r.id = :id
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<WithdrawalRequest> findByStatus(String status, Pageable pageable);

    @Query("""
            select count(r)
            from WithdrawalRequest r
            where r.status = 'PENDING'
            """)
    long countPending();

    @Query("""
            select coalesce(sum(r.amount), 0)
            from WithdrawalRequest r
            where r.status = 'PENDING'
            """)
    BigDecimal sumPendingAmount();

    @Query("""
            select r
            from WithdrawalRequest r
            where r.status = 'PENDING'
            order by r.requestedAt asc, r.id asc
            """)
    List<WithdrawalRequest> findOldestPending(Pageable pageable);

    boolean existsByBankTxnRef(String bankTxnRef);

    @Query("""
            select count(r)
            from WithdrawalRequest r
            where r.status = 'PENDING'
              and r.requestedAt < :threshold
            """)
    long countPendingRequestedBefore(@Param("threshold") OffsetDateTime threshold);

    @Query("""
            select coalesce(sum(r.amount), 0)
            from WithdrawalRequest r
            where r.driverId = :driverId
              and r.status = 'PENDING'
            """)
    BigDecimal sumPendingAmount(@Param("driverId") UUID driverId);
}
