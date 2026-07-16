package vn.movehome.backend.incident;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DriverIncidentReportRepository extends JpaRepository<DriverIncidentReport, UUID> {

    // Chan tao them su co khi don dang co 1 su co mo (PENDING/CONFIRMED).
    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<String> statuses);

    // Tim su co dang mo cua don — dung khi tai xe khac nhan lai (resolve RESOLVED_REASSIGNED).
    Optional<DriverIncidentReport> findFirstByOrderIdAndStatus(UUID orderId, String status);

    Page<DriverIncidentReport> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Page<DriverIncidentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Khoa ghi khi Manager xac nhan / boi thuong — chong double-process.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DriverIncidentReport r where r.id = :id")
    Optional<DriverIncidentReport> findByIdForUpdate(@Param("id") UUID id);
}
