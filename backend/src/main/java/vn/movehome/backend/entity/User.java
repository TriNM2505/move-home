package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Thuc the nguoi dung — anh xa bang "app_user" (KHONG dung "user" vi day la reserved word PostgreSQL).
 * Dai dien cho 4 vai tro: CUSTOMER, DRIVER, MANAGER, ADMIN.
 * Constitution AC-09: soft delete qua cot deleted_at. KHONG dung DELETE truc tiep.
 * Spec #001 Data Model.
 */
@Entity
@Table(
    name = "app_user",
    indexes = {
        // Partial index tren email — chi indexing ban ghi chua bi xoa (AC-09)
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        // Index ket hop role + status cho cac query phan quyen va quan ly
        @Index(name = "idx_user_role_status", columnList = "role,status")
    }
)
// Soft delete: UPDATE thay vi DELETE (AC-09)
@SQLDelete(sql = "UPDATE app_user SET deleted_at = NOW() WHERE id = ?")
// Filter mac dinh: chi tra ve ban ghi chua bi xoa
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    // Email la identifier chinh cho tat ca role (Customer dung username de login nhung luu email)
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Hash BCrypt cost 12 — KHONG bao gio luu plaintext (HR-02, FR-006)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    // Chuan hoa ve +84xxxxxxxxx truoc khi luu (FR-002)
    @Column(length = 20)
    private String phone;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // 4 vai tro — luu enum name truc tiep vao DB (HR-10)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // Trang thai tai khoan/onboarding — xem UserStatus.java de hieu cac chuyen tiep
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFY;

    // Flag tien loi kiem tra email da xac thuc chua (bo sung ngoai viec kiem tra status)
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    // Manager/Admin moi tao phai doi password truoc khi dung he thong (FR-036, FR-037)
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    // So lan nhap sai password lien tiep — reset ve 0 khi dang nhap thanh cong (HR-16, FR-022)
    // Spec reference: failed_login_count (FR-022, FR-034)
    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    // Timestamp lan nhap sai password cuoi — dung cho scheduled job reset counter (FR-034)
    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    // Thoi diem tai khoan duoc tu dong mo khoa — NULL nghia la khong bi khoa (HR-16, FR-021)
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_previous_status", length = 30)
    private UserStatus suspensionPreviousStatus;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_by")
    private UUID suspendedBy;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @Column(name = "suspension_until")
    private Instant suspensionUntil;

    // Tu dong set khi tao — luu UTC (AC-07)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Tu dong cap nhat moi lan entity thay doi — luu UTC (AC-07)
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Soft delete — AC-09. NULL = chua xoa; co gia tri = da xoa logic (khong xuat hien o query thuong)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ===== Spring Security UserDetails =====

    /**
     * Tra ve quyen voi prefix "ROLE_" de Spring Security nhan biet.
     * Vi du: DRIVER → "ROLE_DRIVER". Dung cho @PreAuthorize("hasRole('DRIVER')"). (HR-10)
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Spring Security dung email lam ten dang nhap logic. */
    @Override
    public String getUsername() {
        return email;
    }

    /** Tra ve BCrypt hash — KHONG bao gio tra plaintext password (HR-02). */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Tai khoan het hieu luc khi bi soft-delete (AC-09). */
    @Override
    public boolean isAccountNonExpired() {
        return deletedAt == null;
    }

    /**
     * Tai khoan bi khoa sau 5 lan sai password; tu mo sau khi qua thoi gian lockedUntil (HR-16, FR-021).
     * Locked accounts tra HTTP 423 cho du password dung.
     */
    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED
                && status != UserStatus.SUSPENDED
                && (lockedUntil == null || lockedUntil.isBefore(Instant.now()));
    }

    /** Credentials khong het han — JWT co chu ky rieng kiem soat vong doi (AC-03). */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Authentication chỉ yêu cầu email đã được xác thực. Trạng thái nghiệp vụ như
     * ACTIVE/PENDING_* được kiểm tra tại service tương ứng, không chặn onboarding ở JWT filter.
     */
    @Override
    public boolean isEnabled() {
        return emailVerified;
    }
}
