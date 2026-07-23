package vn.movehome.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
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

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
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
    private Uploader uploader;

    @Mock
    private OrderRepository orderRepository;

    private CustomerProfileService service;

    @BeforeEach
    void setUp() {
        service = new CustomerProfileService(
                userRepository, refreshTokenRepository, passwordEncoder, cloudinary, orderRepository);
    }

    private User customer(UUID id) {
        return User.builder()
                .id(id)
                .email("khach@example.com")
                .passwordHash("hashed-current")
                .fullName("Nguyen Van A")
                .phone("+84912345678")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    // ===== getProfile =====

    @Test
    void getProfileReturnsResponseWithOrderCountForActiveCustomer() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(7L);

        CustomerProfileResponse response = service.getProfile(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.email()).isEqualTo("khach@example.com");
        assertThat(response.phone()).isEqualTo("+84912345678");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.totalOrders()).isEqualTo(7L);
    }

    @Test
    void getProfileThrowsNotFoundWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("PROFILE_NOT_FOUND|Không tìm thấy hồ sơ khách hàng.");
                });
    }

    @Test
    void getProfileThrowsForbiddenWhenUserIsNotCustomer() {
        UUID id = UUID.randomUUID();
        User driver = User.builder().id(id).role(UserRole.DRIVER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> service.getProfile(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo("FORBIDDEN|Bạn không có quyền truy cập hồ sơ khách hàng.");
                });
    }

    // ===== updateProfile =====

    @Test
    void updateProfileNormalizesLocalPhoneAndCollapsesWhitespaceInFullName() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(0L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("  Tran   Thi   B  ");
        request.setPhone("0987654321");

        UpdateProfileResponse response = service.updateProfile(id, request);

        assertThat(user.getFullName()).isEqualTo("Tran Thi B");
        assertThat(user.getPhone()).isEqualTo("+84987654321");
        assertThat(response.message()).isEqualTo("Cập nhật thông tin cá nhân thành công.");
        assertThat(response.profile().phone()).isEqualTo("+84987654321");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileKeepsPhoneAsIsWhenAlreadyInternationalFormat() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(0L);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Le Van C");
        request.setPhone("+84912345678");

        service.updateProfile(id, request);

        assertThat(user.getPhone()).isEqualTo("+84912345678");
    }

    @Test
    void updateProfileRejectsUnknownFieldsAsImmutable() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        ObjectMapper mapper = new ObjectMapper();
        UpdateProfileRequest request = mapper.readValue(
                "{\"fullName\":\"A\",\"phone\":\"0912345678\",\"email\":\"hack@example.com\"}",
                UpdateProfileRequest.class);

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("IMMUTABLE_FIELD|Chỉ được cập nhật họ tên và số điện thoại.");
                });
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileRejectsBlankFullName() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("   ");
        request.setPhone("0912345678");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Họ tên không được để trống.");
                });
    }

    @Test
    void updateProfileRejectsFullNameShorterThanTwoCharacters() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("A");
        request.setPhone("0912345678");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Họ tên phải có từ 2 đến 100 ký tự."));
    }

    @Test
    void updateProfileRejectsFullNameLongerThanOneHundredCharacters() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("A".repeat(101));
        request.setPhone("0912345678");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Họ tên phải có từ 2 đến 100 ký tự."));
    }

    @Test
    void updateProfileRejectsBlankPhone() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Pham Thi D");
        request.setPhone("   ");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số điện thoại không được để trống."));
    }

    @Test
    void updateProfileRejectsInvalidPhonePattern() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Pham Thi D");
        request.setPhone("123456");

        assertThatThrownBy(() -> service.updateProfile(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Số điện thoại không hợp lệ."));
    }

    // ===== updateAvatar =====

    @Test
    void updateAvatarRejectsNullFile() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));

        assertThatThrownBy(() -> service.updateAvatar(id, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
    }

    @Test
    void updateAvatarRejectsEmptyFile() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.updateAvatar(id, emptyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống."));
    }

    @Test
    void updateAvatarRejectsFileLargerThanMaxSize() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        MultipartFile oversized = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(1_572_865L);

        assertThatThrownBy(() -> service.updateAvatar(id, oversized))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("INVALID_FILE|Kích thước ảnh không được vượt quá 1,5 MB."));
    }

    @Test
    void updateAvatarRejectsFileThatCannotBeRead() throws IOException {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        MultipartFile unreadable = org.mockito.Mockito.mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(10L);
        when(unreadable.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> service.updateAvatar(id, unreadable))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên."));
    }

    @Test
    void updateAvatarRejectsContentTooShortToBeAnyRecognizedImage() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        MockMultipartFile tooShort = new MockMultipartFile("file", "x.bin", "application/octet-stream",
                new byte[]{0x01});

        assertThatThrownBy(() -> service.updateAvatar(id, tooShort))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ."));
    }

    @Test
    void updateAvatarRejectsContentWithWrongMagicNumberButLongEnough() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        MockMultipartFile notAnImage = new MockMultipartFile("file", "fake.jpg", "image/jpeg",
                "khong phai la anh that su".getBytes());

        assertThatThrownBy(() -> service.updateAvatar(id, notAnImage))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ."));
    }

    @Test
    void updateAvatarUploadsJpegAndDestroysOldAvatarWhenPresent() throws IOException {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        user.setAvatarUrl("https://res.cloudinary.com/demo/image/upload/old.jpg");
        user.setAvatarPublicId("movehome/customers/old/avatar/old");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/new.jpg");
        uploadResult.put("public_id", "movehome/customers/" + id + "/avatar/new");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02});

        UpdateProfileResponse response = service.updateAvatar(id, jpeg);

        assertThat(response.message()).isEqualTo("Cập nhật ảnh đại diện thành công.");
        assertThat(user.getAvatarUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/new.jpg");
        assertThat(user.getAvatarPublicId()).isEqualTo("movehome/customers/" + id + "/avatar/new");
        verify(uploader).destroy("movehome/customers/old/avatar/old", com.cloudinary.utils.ObjectUtils.emptyMap());
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatarUploadsPngAndSkipsDestroyWhenNoOldAvatar() throws IOException {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/new.png");
        uploadResult.put("public_id", "movehome/customers/" + id + "/avatar/new");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);
        MockMultipartFile png = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00});

        service.updateAvatar(id, png);

        verify(uploader, never()).destroy(any(), anyMap());
    }

    @Test
    void updateAvatarUploadsWebpSuccessfully() throws IOException {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/new.webp");
        uploadResult.put("public_id", "movehome/customers/" + id + "/avatar/new");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);
        byte[] webp = "RIFF0000WEBPfmt ".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", webp);

        UpdateProfileResponse response = service.updateAvatar(id, file);

        assertThat(response.profile().id()).isEqualTo(id);
    }

    @Test
    void updateAvatarThrowsBadGatewayWhenCloudinaryUploadFails() throws IOException {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("cloudinary down"));
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> service.updateAvatar(id, jpeg))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason())
                            .isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateAvatarThrowsBadGatewayWhenCloudinaryResponseMissingSecureUrl() throws IOException {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("public_id", "movehome/customers/x/avatar/y");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(incomplete);
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> service.updateAvatar(id, jpeg))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason())
                            .isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
    }

    @Test
    void updateAvatarThrowsBadGatewayWhenPublicIdIsBlank() throws IOException {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> blankPublicId = new HashMap<>();
        blankPublicId.put("secure_url", "https://res.cloudinary.com/demo/image/upload/new.jpg");
        blankPublicId.put("public_id", "   ");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(blankPublicId);
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> service.updateAvatar(id, jpeg))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void updateAvatarThrowsBadGatewayWhenSecureUrlDoesNotStartWithHttps() throws IOException {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> insecure = new HashMap<>();
        insecure.put("secure_url", "http://res.cloudinary.com/demo/image/upload/new.jpg");
        insecure.put("public_id", "movehome/customers/x/avatar/y");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(insecure);
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> service.updateAvatar(id, jpeg))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void updateAvatarSucceedsEvenWhenDestroyingOldAvatarThrows() throws IOException {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        user.setAvatarUrl("https://res.cloudinary.com/demo/image/upload/old.jpg");
        user.setAvatarPublicId("movehome/customers/old/avatar/old");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(id)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/new.jpg");
        uploadResult.put("public_id", "movehome/customers/" + id + "/avatar/new");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);
        when(uploader.destroy(any(), anyMap())).thenThrow(new IOException("cannot destroy"));
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        UpdateProfileResponse response = service.updateAvatar(id, jpeg);

        assertThat(response.message()).isEqualTo("Cập nhật ảnh đại diện thành công.");
        verify(userRepository).save(user);
    }

    // ===== changePassword =====

    @Test
    void changePasswordUpdatesHashAndRevokesSessionsOnSuccess() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@123456", "hashed-current")).thenReturn(true);
        when(passwordEncoder.matches("New@123456", "hashed-current")).thenReturn(false);
        when(passwordEncoder.encode("New@123456")).thenReturn("hashed-new");
        ChangePasswordRequest request = new ChangePasswordRequest("Old@123456", "New@123456");

        ChangePasswordResponse response = service.changePassword(id, request);

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
        assertThat(response.message()).isEqualTo("Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.");
        assertThat(response.forceRelogin()).isTrue();
        assertThat(response.sessionsRevoked()).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(id);
    }

    @Test
    void changePasswordRejectsBlankCurrentPassword() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        ChangePasswordRequest request = new ChangePasswordRequest("   ", "New@123456");

        assertThatThrownBy(() -> service.changePassword(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("VALIDATION_ERROR|Mật khẩu hiện tại không được để trống."));
    }

    @Test
    void changePasswordRejectsNullCurrentPassword() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        ChangePasswordRequest request = new ChangePasswordRequest(null, "New@123456");

        assertThatThrownBy(() -> service.changePassword(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("VALIDATION_ERROR|Mật khẩu hiện tại không được để trống."));
    }

    @Test
    void changePasswordRejectsBlankNewPassword() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        ChangePasswordRequest request = new ChangePasswordRequest("Old@123456", "   ");

        assertThatThrownBy(() -> service.changePassword(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .isEqualTo("VALIDATION_ERROR|Mật khẩu mới không được để trống."));
    }

    @Test
    void changePasswordRejectsWeakNewPasswordFailingPolicy() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(customer(id)));
        ChangePasswordRequest request = new ChangePasswordRequest("Old@123456", "weakpass");

        assertThatThrownBy(() -> service.changePassword(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(
                                "VALIDATION_ERROR|Mật khẩu mới phải dài 8-64 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt."));
    }

    @Test
    void changePasswordRejectsWhenCurrentPasswordDoesNotMatch() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong@123456", "hashed-current")).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("Wrong@123456", "New@123456");

        assertThatThrownBy(() -> service.changePassword(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("INVALID_CURRENT_PASSWORD|Mật khẩu hiện tại không đúng.");
                });
    }

    @Test
    void changePasswordRejectsWhenNewPasswordSameAsCurrent() {
        UUID id = UUID.randomUUID();
        User user = customer(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Old@123456", "hashed-current")).thenReturn(true);
        ChangePasswordRequest request = new ChangePasswordRequest("Old@123456", "Old@123456");

        assertThatThrownBy(() -> service.changePassword(id, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason())
                            .isEqualTo("PASSWORD_REUSE_NOT_ALLOWED|Mật khẩu mới không được trùng mật khẩu hiện tại.");
                });
    }

    // ===================== statusLabel (private helper, qua reflection) =====================
    // statusLabel() hien khong duoc goi tu bat ky public method nao trong CustomerProfileService
    // (nghi van dead code / thieu wiring) — dung reflection de phu tat ca nhanh switch.

    private String invokeStatusLabel(User user) throws Exception {
        java.lang.reflect.Method method = CustomerProfileService.class.getDeclaredMethod("statusLabel", User.class);
        method.setAccessible(true);
        return (String) method.invoke(service, user);
    }

    @Test
    void statusLabelReturnsVietnameseTextForActive() throws Exception {
        User user = customer(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);

        assertThat(invokeStatusLabel(user)).isEqualTo("Đang hoạt động");
    }

    @Test
    void statusLabelReturnsVietnameseTextForPendingVerify() throws Exception {
        User user = customer(UUID.randomUUID());
        user.setStatus(UserStatus.PENDING_VERIFY);

        assertThat(invokeStatusLabel(user)).isEqualTo("Chờ xác thực email");
    }

    @Test
    void statusLabelReturnsVietnameseTextForSuspended() throws Exception {
        User user = customer(UUID.randomUUID());
        user.setStatus(UserStatus.SUSPENDED);

        assertThat(invokeStatusLabel(user)).isEqualTo("Tạm khóa");
    }

    @Test
    void statusLabelFallsBackToEnumNameForOtherStatuses() throws Exception {
        User user = customer(UUID.randomUUID());
        user.setStatus(UserStatus.LOCKED);

        assertThat(invokeStatusLabel(user)).isEqualTo("LOCKED");
    }
}
