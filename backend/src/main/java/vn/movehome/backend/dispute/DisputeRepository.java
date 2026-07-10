package vn.movehome.backend.dispute;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<String> statuses);

    Page<Dispute> findByStatus(String status, Pageable pageable);

    // Khoan phat dang cho nop bo sung cua 1 tai xe (banner FE)
    Optional<Dispute> findFirstByDriverIdAndPendingDeductShortfallIsNotNullOrderByDeductDeadlineAsc(UUID driverId);

    // Cac khoan qua han nop bo sung — scheduled job khoa tai khoan + tru coc
    @Query("""
            select d.id
            from Dispute d
            where d.pendingDeductShortfall is not null
              and d.deductDeadline < :now
            """)
    List<UUID> findExpiredPendingDeductionIds(@Param("now") OffsetDateTime now);
}
