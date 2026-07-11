package vn.movehome.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.CustomerWallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<CustomerWallet, UUID> {

    Optional<CustomerWallet> findByCustomerId(UUID customerId);

    // Load nhieu vi theo danh sach customer — dung cho hang doi Admin duyet rut tien.
    List<CustomerWallet> findByCustomerIdIn(Collection<UUID> customerIds);

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
