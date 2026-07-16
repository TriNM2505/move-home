package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import com.cloudinary.Cloudinary;
import vn.movehome.backend.dto.customer.profile.ChangePasswordRequest;
import vn.movehome.backend.dto.customer.profile.ChangePasswordResponse;
import vn.movehome.backend.dto.customer.profile.CustomerProfileResponse;
import vn.movehome.backend.dto.customer.profile.UpdateProfileRequest;
import vn.movehome.backend.dto.customer.profile.UpdateProfileResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.RefreshTokenRepository;
import vn.movehome.backend.repository.UserRepository;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private OrderRepository orderRepository;

    private CustomerProfileService service;

    @BeforeEach
    void setUp() {
        service = new CustomerProfileService(userRepository, refreshTokenRepository, passwordEncoder, cloudinary, orderRepository);
    }

    @Test
    void getProfileReturnsCustomerInfo() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Nguyễn Văn A", "customer@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        CustomerProfileResponse response = service.getProfile(id);

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.email()).isEqualTo("customer@test.vn");
    }

    @Test
    void getProfileThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getProfileThrowsForbiddenForNonCustomerRole() {
        UUID id = UUID.randomUUID();
        User driver = User.builder()
                .id(id).email("driver@movehome.vn").role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE).emailVerified(true)
                .fullName("Driver A")
                .build();
        when(userRepository.findById(id)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> service.getProfile(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateProfileSavesNormalizedName() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Old Name", "customer@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("  Nguyễn Thị   Bình  ");
        request.setPhone("0987654321");

        UpdateProfileResponse response = service.updateProfile(id, request);

        assertThat(response.profile().fullName()).isEqualTo("Nguyễn Thị Bình");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileNormalizesPhoneNumberTo84Prefix() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer B", "b@test.vn", "+84900000000");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Customer B");
        request.setPhone("0912345678");

        service.updateProfile(id, request);

        assertThat(user.getPhone()).isEqualTo("+84912345678");
    }

    @Test
    void updateProfileRejectsInvalidPhone() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer C", "c@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Customer C");
        request.setPhone("12345678"); // Khong dung dinh dang VN

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void changePasswordSucceeds() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer D", "d@test.vn", "+84900000001");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass@456", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass@456")).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(user);

        ChangePasswordResponse response = service.changePassword(id,
                new ChangePasswordRequest("OldPass@123", "NewPass@456"));

        assertThat(response.forceRelogin()).isTrue();
        verify(refreshTokenRepository).revokeAllByUserId(any());
    }

    @Test
    void changePasswordRejectsWeakNewPassword() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer E", "e@test.vn", "+84900000002");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest("OldPass@123", "tooweak")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer F", "f@test.vn", "+84900000003");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass@123", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest("WrongPass@123", "NewPass@456!")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void changePasswordRejectsSamePasswordAsOld() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer G", "g@test.vn", "+84900000004");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SamePass@123", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest("SamePass@123", "SamePass@123")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ===== MISSING BRANCHES — updateProfile unknownFields =====

    /**
     * Kich ban: UpdateProfileRequest co chua truong khong xac dinh (ví du "email").
     * Ket qua mong doi: Nem 422 UNPROCESSABLE_ENTITY voi ma loi IMMUTABLE_FIELD.
     */
    @Test
    void updateProfileRejectsRequestWithUnknownFields() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer A", "a@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        // Them truong "email" khong duoc phep vao request bang reflection (setUnknownField la package-private)
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Customer A");
        request.setPhone("0912345678");
        // Dung reflection de them unknown field vao Set (vi setUnknownField la package-private)
        try {
            Field unknownFieldsField = UpdateProfileRequest.class.getDeclaredField("unknownFields");
            unknownFieldsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> unknownFields = (Set<String>) unknownFieldsField.get(request);
            unknownFields.add("email");
        } catch (Exception e) {
            throw new RuntimeException("Khong the truy cap unknownFields bang reflection", e);
        }

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("IMMUTABLE_FIELD|");
                });
    }

    // ===== MISSING BRANCHES — changePassword blank inputs =====

    /**
     * Kich ban: currentPassword la null.
     * Ket qua mong doi: Nem 422 VALIDATION_ERROR.
     */
    @Test
    void changePasswordRejectsNullCurrentPassword() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer H", "h@test.vn", "+84900000005");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest(null, "NewPass@456")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Kich ban: currentPassword la chuoi trong "  " (blank).
     * Ket qua mong doi: Nem 422 VALIDATION_ERROR.
     */
    @Test
    void changePasswordRejectsBlankCurrentPassword() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer I", "i@test.vn", "+84900000006");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest("   ", "NewPass@456")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Kich ban: newPassword la null.
     * Ket qua mong doi: Nem 422 VALIDATION_ERROR.
     */
    @Test
    void changePasswordRejectsNullNewPassword() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer J", "j@test.vn", "+84900000007");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest("OldPass@123", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Kich ban: newPassword la chuoi trong (blank).
     * Ket qua mong doi: Nem 422 VALIDATION_ERROR.
     */
    @Test
    void changePasswordRejectsBlankNewPassword() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer K", "k@test.vn", "+84900000008");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordRequest("OldPass@123", "")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ===== MISSING BRANCHES — normalizePhone already +84 prefix =====

    /**
     * Kich ban: so dien thoai da o dang +84 (khong bat dau bang '0').
     * Ket qua mong doi: giu nguyen, khong them tien to +84 lan 2.
     */
    @Test
    void updateProfilePhoneAlreadyInternationalFormatRetainedAsIs() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer L", "l@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Customer L");
        request.setPhone("+84987654321"); // Da co +84, khong bat dau bang '0'

        service.updateProfile(id, request);

        // Phone duoc giu nguyen la +84987654321
        assertThat(user.getPhone()).isEqualTo("+84987654321");
    }

    // ===== MISSING BRANCHES — validateFullName boundary =====

    /**
     * Kich ban: fullName la null → validateFullName nem 422 VALIDATION_ERROR.
     * Cover CustomerProfileService:122 — nhanh fullName == null.
     */
    @Test
    void updateProfileRejectsNullFullName() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer M1", "m1@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(null);
        request.setPhone("0912345678");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Kich ban: phone la null (fullName hop le) → normalizePhone nem 422 VALIDATION_ERROR.
     * Cover CustomerProfileService:133 — nhanh phone == null.
     */
    @Test
    void updateProfileRejectsNullPhone() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer M2", "m2@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Customer M2"); // fullName hop le
        request.setPhone(null);             // phone null → normalizePhone nem 422

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Kich ban: fullName chi co 1 ky tu (qua ngan, < 2).
     * Ket qua mong doi: Nem 422 VALIDATION_ERROR.
     */
    @Test
    void updateProfileRejectsTooShortFullName() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer M", "m@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("A"); // 1 ky tu — qua ngan
        request.setPhone("0912345678");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Kich ban: fullName co 101 ky tu (qua dai, > 100).
     * Ket qua mong doi: Nem 422 VALIDATION_ERROR.
     */
    @Test
    void updateProfileRejectsTooLongFullName() {
        UUID id = UUID.randomUUID();
        User user = customer(id, "Customer N", "n@test.vn", "+84912345678");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        // Tao ten co 101 ky tu
        String veryLongName = "A".repeat(101);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(veryLongName);
        request.setPhone("0912345678");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private User customer(UUID id, String fullName, String email, String phone) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }
}
