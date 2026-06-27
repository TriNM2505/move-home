package vn.movehome.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.dto.admin.detail.CustomerDetailResponse;
import vn.movehome.backend.entity.WalletTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Page<WalletTransaction> findByUserId(UUID userId, Pageable pageable);

    @Query("""
            select new vn.movehome.backend.dto.admin.detail.CustomerDetailResponse$RecentWalletTransactionItem(
                wt.id,
                wt.type,
                wt.amount,
                null,
                wt.createdAt,
                null
            )
            from WalletTransaction wt
            where wt.userId = :userId
            order by wt.createdAt desc, wt.id desc
            """)
    List<CustomerDetailResponse.RecentWalletTransactionItem> findRecentByUserId(
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query("""
            select coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.userId = :userId
              and wt.type = 'WALLET_TOP_UP'
            """)
    BigDecimal sumTopUpByUserId(@Param("userId") UUID userId);
}
