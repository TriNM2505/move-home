package vn.movehome.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.movehome.backend.entity.DriverProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho bang driver_profile.
 * Khong co soft delete tren DriverProfile — xoa cascade khi User bi xoa.
 */
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    /**
     * Tim ho so Driver theo userId (FK 1-1 voi app_user).
     * Dung khi load trang profile Driver hoac khi Manager xem ho so tai xe.
     */
    Optional<DriverProfile> findByUserId(UUID userId);

    boolean existsByVehiclePlateAndUserIdNot(String vehiclePlate, UUID userId);

    /**
     * Top 5 Driver co tong thu nhap cao nhat.
     * Dung cho Admin Dashboard leaderboard (tuy chon — Phase 3B).
     */
    List<DriverProfile> findTop5ByOrderByTotalRevenueDesc();

    /**
     * Dem so Driver da duoc duyet (approvedAt != NULL).
     * Dung cho KPI "tong so Driver da tung ACTIVE" trong bao cao (Phase 3B).
     */
    long countByApprovedAtIsNotNull();

    @Query("select dp from DriverProfile dp " +
            "join User u on u.id = dp.userId " +
            "where u.role = vn.movehome.backend.entity.UserRole.DRIVER " +
            "  and u.status = vn.movehome.backend.entity.UserStatus.PENDING_APPROVAL " +
            "  and u.deletedAt is null")
    Page<DriverProfile> findPendingApproval(Pageable pageable);

    @Query("select dp from DriverProfile dp " +
            "join User u on u.id = dp.userId " +
            "where u.role = vn.movehome.backend.entity.UserRole.DRIVER " +
            "  and u.status = vn.movehome.backend.entity.UserStatus.REJECTED " +
            "  and u.deletedAt is null")
    Page<DriverProfile> findRejected(Pageable pageable);
}
