package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy van bang app_user.
 * @SQLRestriction("deleted_at IS NULL") duoc apply tu dong qua entity — mac dinh chi tra ban ghi chua xoa.
 * De truy van ca ban ghi da xoa (Admin/audit), dung EntityManager voi @SQLRestriction disabled hoac raw JPQL.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Tim nguoi dung theo email (mac dinh filter deleted_at IS NULL tu @SQLRestriction).
     * Dung cho kiem tra trung email khi dang ky (FR-004, FR-043) va tra ve info sau login.
     */
    Optional<User> findByEmail(String email);

    /**
     * Tim nguoi dung theo email, bao dam chua bi xoa (soft-delete aware, explicit check).
     * Dung trong AuthService khi can chac chan user hop le truoc khi xu ly login.
     * Luu y: @SQLRestriction da filter mac dinh, phuong thuc nay la explicit redundant check de ro rang.
     */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findFirstByRoleAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
            UserRole role,
            UserStatus status);

    /**
     * Kiem tra email da duoc su dung chua (bao gom ca tai khoan da soft-delete).
     * Can kiem tra ca deleted_at IS NOT NULL de tranh tai su dung email cu — FR-004, FR-043.
     * @param email email can kiem tra (da lowercase)
     */
    boolean existsByEmail(String email);

    /**
     * Lay danh sach nguoi dung theo role va status, chi lay ban ghi chua bi xoa.
     * Dung cho: Manager xem danh sach Driver PENDING_APPROVAL (FR-064),
     * Admin xem danh sach Driver ACTIVE, v.v.
     */
    List<User> findByRoleAndStatusAndDeletedAtIsNull(UserRole role, UserStatus status);

    /**
     * Dem so nguoi dung theo role va status, chi dem ban ghi chua bi xoa.
     * Dung cho KPI Dashboard: so Driver ACTIVE, Customer ACTIVE, Driver PENDING_APPROVAL, v.v. (Spec #028)
     */
    long countByRoleAndStatusAndDeletedAtIsNull(UserRole role, UserStatus status);

    /**
     * Dem tong so nguoi dung theo role (bat ky status, chua bi xoa).
     * Dung cho bao cao tong hop: tong so Driver da dang ky, tong so Customer, v.v. (Spec #028 Admin Dashboard)
     */
    long countByRoleAndDeletedAtIsNull(UserRole role);

    // ===== Phase 3C: Admin Driver / Customer List =====

    /**
     * Danh sach tai xe cho Admin — LEFT JOIN voi driver_profile (Phase 3C).
     * Tra Object[]: [user_id, full_name, email, phone, status, license_number,
     *               vehicle_plate, vehicle_type, deposit_amount, total_orders_completed,
     *               total_revenue, average_rating, created_at, approved_at]
     * Cac field tu driver_profile co the null neu Driver chua submit ho so (status PENDING_VERIFY).
     */
    @Query(
        value = """
            SELECT u.id, u.full_name, u.email, u.phone, u.status,
                   dp.license_number, dp.vehicle_plate, dp.vehicle_type,
                   dp.deposit_amount, dp.total_orders_completed, dp.total_revenue, dp.average_rating,
                   u.created_at, dp.approved_at
            FROM app_user u
            LEFT JOIN driver_profile dp ON dp.user_id = u.id
            WHERE u.role = 'DRIVER' AND u.deleted_at IS NULL
            ORDER BY u.created_at DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAllDriversForAdmin();

    /**
     * Tai xe cho Admin, loc theo status cu the (Phase 3C).
     * @param status gia tri enum name: ACTIVE / PENDING_APPROVAL / SUSPENDED / etc.
     */
    @Query(
        value = """
            SELECT u.id, u.full_name, u.email, u.phone, u.status,
                   dp.license_number, dp.vehicle_plate, dp.vehicle_type,
                   dp.deposit_amount, dp.total_orders_completed, dp.total_revenue, dp.average_rating,
                   u.created_at, dp.approved_at
            FROM app_user u
            LEFT JOIN driver_profile dp ON dp.user_id = u.id
            WHERE u.role = 'DRIVER' AND u.deleted_at IS NULL AND u.status = :status
            ORDER BY u.created_at DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAllDriversByStatusForAdmin(@Param("status") String status);

    /**
     * Danh sach khach hang cho Admin, kem tong so don da dat (Phase 3C).
     * Tra Object[]: [user_id, full_name, email, phone, status, email_verified,
     *               total_orders_placed, created_at]
     * LEFT JOIN service_order: ke ca don CANCELLED, chi bo qua don da soft-delete.
     */
    @Query(
        value = """
            SELECT u.id, u.full_name, u.email, u.phone, u.status, u.email_verified,
                   COUNT(o.id) AS total_orders_placed,
                   u.created_at
            FROM app_user u
            LEFT JOIN service_order o ON o.customer_id = u.id AND o.deleted_at IS NULL
            WHERE u.role = 'CUSTOMER' AND u.deleted_at IS NULL
            GROUP BY u.id, u.full_name, u.email, u.phone, u.status, u.email_verified, u.created_at
            ORDER BY u.created_at DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAllCustomersForAdmin();

    /**
     * Khach hang cho Admin, loc theo status cu the (Phase 3C).
     * @param status gia tri enum name: ACTIVE / PENDING_VERIFY / SUSPENDED
     */
    @Query(
        value = """
            SELECT u.id, u.full_name, u.email, u.phone, u.status, u.email_verified,
                   COUNT(o.id) AS total_orders_placed,
                   u.created_at
            FROM app_user u
            LEFT JOIN service_order o ON o.customer_id = u.id AND o.deleted_at IS NULL
            WHERE u.role = 'CUSTOMER' AND u.deleted_at IS NULL AND u.status = :status
            GROUP BY u.id, u.full_name, u.email, u.phone, u.status, u.email_verified, u.created_at
            ORDER BY u.created_at DESC
            """,
        nativeQuery = true
    )
    List<Object[]> findAllCustomersByStatusForAdmin(@Param("status") String status);
}
