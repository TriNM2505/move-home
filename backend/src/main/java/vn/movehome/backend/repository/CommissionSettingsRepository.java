package vn.movehome.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vn.movehome.backend.entity.CommissionSettings;

import java.util.Optional;

public interface CommissionSettingsRepository extends JpaRepository<CommissionSettings, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CommissionSettings s where s.id = 1")
    Optional<CommissionSettings> findActiveForUpdate();

    default Optional<CommissionSettings> findActive() {
        return findById(1);
    }
}
