package vn.movehome.backend.driver.finance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.dto.admin.list.WithdrawalListItemRaw;

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

        @Query("""
                        select new vn.movehome.backend.dto.admin.list.WithdrawalListItemRaw(
                            wr.id,
                            wr.driverId,
                            driver.fullName,
                            wr.amount,
                            wr.bankNameSnapshot,
                            wr.bankAccountNumber,
                            wr.status,
                            wr.requestedAt,
                            wr.processedAt,
                            processor.fullName,
                            wr.bankTxnRef
                        )
                        from WithdrawalRequest wr
                        join User driver on driver.id = wr.driverId
                        left join User processor on processor.id = wr.processedBy
                        where (coalesce(:status, '') = '' or wr.status = :status)
                          and (
                              coalesce(:search, '') = ''
                              or lower(driver.fullName) like lower(concat('%', coalesce(:search, ''), '%'))
                              or lower(wr.bankTxnRef) like lower(concat('%', coalesce(:search, ''), '%'))
                          )
                          and (cast(:from as java.time.OffsetDateTime) is null or wr.requestedAt >= :from)
                          and (cast(:to as java.time.OffsetDateTime) is null or wr.requestedAt < :to)
                        """)
        Page<WithdrawalListItemRaw> findAdminWithdrawalList(
                        @Param("status") String status,
                        @Param("search") String search,
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to,
                        Pageable pageable);

        @Query("""
                        select new vn.movehome.backend.dto.admin.detail.DriverDetailResponse$RecentWithdrawalItem(
                            wr.id,
                            wr.amount,
                            wr.status,
                            wr.requestedAt,
                            wr.processedAt
                        )
                        from WithdrawalRequest wr
                        where wr.driverId = :driverId
                        order by wr.requestedAt desc, wr.id desc
                        """)
        List<DriverDetailResponse.RecentWithdrawalItem> findRecentWithdrawalsByDriver(
                        @Param("driverId") UUID driverId,
                        Pageable pageable);

        @Query("""
                        select coalesce(sum(wr.amount), 0)
                        from WithdrawalRequest wr
                        where wr.driverId = :driverId
                          and wr.status = 'PROCESSED'
                        """)
        BigDecimal sumProcessedAmountByDriver(@Param("driverId") UUID driverId);
}
