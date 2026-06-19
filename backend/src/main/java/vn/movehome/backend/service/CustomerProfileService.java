package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.customer.profile.ChangePasswordRequest;
import vn.movehome.backend.dto.customer.profile.ChangePasswordResponse;
import vn.movehome.backend.dto.customer.profile.CustomerProfileResponse;
import vn.movehome.backend.dto.customer.profile.UpdateProfileRequest;
import vn.movehome.backend.dto.customer.profile.UpdateProfileResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.repository.RefreshTokenRepository;
import vn.movehome.backend.repository.UserRepository;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)(3|5|7|8|9)[0-9]{8}$");
    private static final Pattern PASSWORD_POLICY = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(UUID customerId) {
        User user = loadCustomer(customerId);
        return toResponse(user);
    }

    @Transactional
    public UpdateProfileResponse updateProfile(UUID customerId, UpdateProfileRequest request) {
        User user = loadCustomer(customerId);

        if (!request.unknownFields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "IMMUTABLE_FIELD|Chỉ được cập nhật họ tên và số điện thoại.");
        }

        String fullName = validateFullName(request.fullName());
        String phone = normalizePhone(request.phone());

        user.setFullName(fullName);
        user.setPhone(phone);
        userRepository.save(user);

        return new UpdateProfileResponse(
                toResponse(user),
                "Cập nhật thông tin cá nhân thành công."
        );
    }

    @Transactional
    public ChangePasswordResponse changePassword(UUID customerId, ChangePasswordRequest request) {
        User user = loadCustomer(customerId);

        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw validationError("Mật khẩu hiện tại không được để trống.");
        }
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw validationError("Mật khẩu mới không được để trống.");
        }
        if (!PASSWORD_POLICY.matcher(request.newPassword()).matches()) {
            throw validationError("Mật khẩu mới phải dài 8-64 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CURRENT_PASSWORD|Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PASSWORD_REUSE_NOT_ALLOWED|Mật khẩu mới không được trùng mật khẩu hiện tại.");
        }

        // HR-02: chỉ lưu BCrypt hash cost 12 qua PasswordEncoder bean, không lưu/log plaintext.
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());

        return new ChangePasswordResponse(
                "Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.",
                true,
                true
        );
    }

    private User loadCustomer(UUID customerId) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "PROFILE_NOT_FOUND|Không tìm thấy hồ sơ khách hàng."));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN|Bạn không có quyền truy cập hồ sơ khách hàng.");
        }
        return user;
    }

    private CustomerProfileResponse toResponse(User user) {
        return new CustomerProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                null,
                user.getStatus().name()
        );
    }

    private String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw validationError("Họ tên không được để trống.");
        }
        String trimmed = fullName.strip().replaceAll("\\s+", " ");
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            throw validationError("Họ tên phải có từ 2 đến 100 ký tự.");
        }
        return trimmed;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw validationError("Số điện thoại không được để trống.");
        }
        String trimmed = phone.strip();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw validationError("Số điện thoại không hợp lệ.");
        }
        if (trimmed.startsWith("0")) {
            return "+84" + trimmed.substring(1);
        }
        return trimmed;
    }

    private String statusLabel(User user) {
        return switch (user.getStatus()) {
            case ACTIVE -> "Đang hoạt động";
            case PENDING_VERIFY -> "Chờ xác thực email";
            case SUSPENDED -> "Tạm khóa";
            default -> user.getStatus().name();
        };
    }

    private ResponseStatusException validationError(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR|" + message);
    }
}
