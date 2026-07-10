package vn.movehome.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.customer.profile.ChangePasswordRequest;
import vn.movehome.backend.dto.customer.profile.ChangePasswordResponse;
import vn.movehome.backend.dto.customer.profile.CustomerProfileResponse;
import vn.movehome.backend.dto.customer.profile.UpdateProfileRequest;
import vn.movehome.backend.dto.customer.profile.UpdateProfileResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.RefreshTokenRepository;
import vn.movehome.backend.repository.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerProfileService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)(3|5|7|8|9)[0-9]{8}$");
    private static final Pattern PASSWORD_POLICY = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$");

    // Gioi han 1.5MB giong DriverDocumentService (AC-10: FE compress truoc, BE chan cung)
    private static final long MAX_AVATAR_SIZE = 1_572_864L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Cloudinary cloudinary;
    // Repo cua order.ServiceOrder (entity dung that) — dem so don cua khach cho profile
    private final OrderRepository orderRepository;

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

    /**
     * Upload avatar qua Cloudinary signed upload server-side (AC-10).
     * Public delivery (khong "authenticated" type) — avatar khong phai du lieu nhay cam
     * nhu GPLX/anh tranh chap, URL hien thi truc tiep khong can sign lai moi lan render.
     * Anh cu bi destroy tren Cloudinary khi thay anh moi (AC-10 cleanup, tranh rac free tier).
     */
    @Transactional
    public UpdateProfileResponse updateAvatar(UUID customerId, MultipartFile file) {
        User user = loadCustomer(customerId);

        byte[] content = validateAndReadAvatar(file);
        String oldPublicId = user.getAvatarPublicId();

        // Upload anh moi truoc — neu fail thi avatar cu van con nguyen
        Map<?, ?> uploadResult;
        try {
            uploadResult = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", "movehome/customers/%s/avatar".formatted(customerId)));
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.", ex);
        }

        Object secureUrl = uploadResult.get("secure_url");
        Object publicId = uploadResult.get("public_id");
        if (!(secureUrl instanceof String url) || !url.startsWith("https://")
                || !(publicId instanceof String pid) || pid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
        }

        user.setAvatarUrl(url);
        user.setAvatarPublicId(pid);
        userRepository.save(user);

        // Xoa anh cu tren Cloudinary — loi xoa khong lam fail viec thay avatar (chi log)
        if (oldPublicId != null && !oldPublicId.isBlank()) {
            try {
                cloudinary.uploader().destroy(oldPublicId, ObjectUtils.emptyMap());
            } catch (IOException | RuntimeException ex) {
                log.warn("Khong the xoa avatar cu tren Cloudinary (public_id={}): {}",
                        oldPublicId, ex.getMessage());
            }
        }

        return new UpdateProfileResponse(
                toResponse(user),
                "Cập nhật ảnh đại diện thành công."
        );
    }

    // Validate giong DriverDocumentService: khong rong, <= 1.5MB, magic number JPEG/PNG/WebP
    private byte[] validateAndReadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw avatarError("Tệp tải lên không được để trống.");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw avatarError("Kích thước ảnh không được vượt quá 1,5 MB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw avatarError("Không thể đọc tệp tải lên.");
        }
        if (!isJpeg(content) && !isPng(content) && !isWebp(content)) {
            throw avatarError("Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
        }
        return content;
    }

    private ResponseStatusException avatarError(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILE|" + message);
    }

    // Kiem tra magic number (byte dau file) thay vi tin Content-Type header (AC-10)
    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
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
                user.getAvatarUrl(),
                user.getStatus().name(),
                user.getCreatedAt(),
                // Dem don that tu DB thay vi de FE hien so mock (bo "12 don" hardcode)
                orderRepository.countByCustomerIdAndDeletedAtIsNull(user.getId())
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
