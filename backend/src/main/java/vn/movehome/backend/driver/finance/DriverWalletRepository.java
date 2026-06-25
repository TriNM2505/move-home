package vn.movehome.backend.driver.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface DriverWalletRepository extends JpaRepository<DriverWallet, UUID> {

    Optional<DriverWallet> findByDriverId(UUID driverId);

    List<DriverWallet> findByDriverIdIn(Collection<UUID> driverIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from DriverWallet w where w.driverId = :driverId")
    Optional<DriverWallet> findByDriverIdForUpdate(@Param("driverId") UUID driverId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO driver_wallet (driver_id, balance, total_earned, total_withdrawn)
            VALUES (:driverId, 0, 0, 0)
            ON CONFLICT (driver_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("driverId") UUID driverId);
}
