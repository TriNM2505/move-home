package vn.movehome.backend.driver.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
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
            select coalesce(sum(r.amount), 0)
            from WithdrawalRequest r
            where r.driverId = :driverId
              and r.status = 'PENDING'
            """)
    BigDecimal sumPendingAmount(@Param("driverId") UUID driverId);
}
