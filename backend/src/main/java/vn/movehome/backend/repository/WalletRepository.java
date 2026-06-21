package vn.movehome.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.CustomerWallet;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<CustomerWallet, UUID> {

    Optional<CustomerWallet> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from CustomerWallet w where w.customerId = :customerId")
    Optional<CustomerWallet> findByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO customer_wallet (customer_id, balance, total_topped_up, total_spent)
            VALUES (:customerId, 0, 0, 0)
            ON CONFLICT (customer_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("customerId") UUID customerId);
}
