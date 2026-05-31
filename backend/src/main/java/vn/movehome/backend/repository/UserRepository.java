package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
