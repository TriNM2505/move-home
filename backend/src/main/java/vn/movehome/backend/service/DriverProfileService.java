package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.driver.onboarding.DriverProfileResponse;
import vn.movehome.backend.dto.driver.onboarding.UpdateDriverProfileRequest;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.repository.DriverProfileRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DriverProfileService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern LICENSE_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9]{8,20}$");
    private static final Pattern VEHICLE_PLATE_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{1,2}-[0-9]{4,5}$");
    private static final Set<String> LICENSE_CLASSES = Set.of("B1", "B2", "C", "D");
    private static final Set<String> VEHICLE_TYPES = Set.of("TRUCK_500KG", "TRUCK_1T", "TRUCK_15T");

    private final DriverProfileRepository driverProfileRepository;

    @Transactional(readOnly = true)
    public DriverProfileResponse getProfile(UUID driverId) {
        return toResponse(loadProfile(driverId));
    }

    @Transactional
    public DriverProfileResponse updateProfile(UUID driverId, UpdateDriverProfileRequest request) {
        rejectUnknownFields(request.unknownFields());

        DriverProfile profile = loadProfile(driverId);
        guardProfileEditable(profile);
        profile.setLicenseNumber(normalizeLicenseNumber(request.licenseNumber()));
        profile.setLicenseClass(normalizeLicenseClass(request.licenseClass()));
        profile.setLicenseExpiryDate(validateLicenseExpiryDate(request.licenseExpiryDate()));
        profile.setVehiclePlate(normalizeVehiclePlate(request.vehiclePlate()));
        profile.setVehicleType(normalizeVehicleType(request.vehicleType()));
        profile.setVehicleCapacityKg(validateVehicleCapacityKg(request.vehicleCapacityKg()));

        try {
            return toResponse(driverProfileRepository.saveAndFlush(profile));
        } catch (OptimisticLockingFailureException ex) {
            throw optimisticLockConflict(ex);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "LICENSE_NUMBER_ALREADY_USED|Số giấy phép lái xe đã được sử dụng.", ex);
        }
    }

    @Transactional
    public DriverProfileResponse submitProfile(UUID driverId) {
        DriverProfile profile = loadProfile(driverId);

        if (profile.getApprovedAt() != null) {
            throw invalidOnboardingStep("Hồ sơ tài xế đã được duyệt, không thể nộp lại.");
        }
        if (profile.getOnboardingCompletedAt() != null) {
            throw invalidOnboardingStep("Hồ sơ đã được nộp và đang chờ duyệt.");
        }

        validateProfileCompleteForSubmit(profile);
        profile.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));

        try {
            return toResponse(driverProfileRepository.saveAndFlush(profile));
        } catch (OptimisticLockingFailureException ex) {
            throw optimisticLockConflict(ex);
        }
    }

    private DriverProfile loadProfile(UUID driverId) {
        return driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ONBOARDING_PROFILE_NOT_FOUND|Không tìm thấy hồ sơ tài xế."));
    }

    private void guardProfileEditable(DriverProfile profile) {
        if (profile.getApprovedAt() != null) {
            throw invalidOnboardingStep("Hồ sơ tài xế đã được duyệt, không thể chỉnh sửa.");
        }
        if (profile.getOnboardingCompletedAt() != null) {
            throw invalidOnboardingStep("Hồ sơ đã được nộp và đang chờ duyệt, không thể chỉnh sửa.");
        }
    }

    private void rejectUnknownFields(Set<String> unknownFields) {
        if (!unknownFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "IMMUTABLE_FIELD|Chỉ được cập nhật thông tin giấy phép lái xe và xe. Trường không được phép: "
                            + String.join(", ", unknownFields) + ".");
        }
    }

    private String normalizeLicenseNumber(String licenseNumber) {
        String normalized = licenseNumber.strip().toUpperCase(Locale.ROOT);
        if (!LICENSE_NUMBER_PATTERN.matcher(normalized).matches()) {
            throw validationError("Số giấy phép lái xe phải gồm 8-20 ký tự chữ hoặc số.");
        }
        return normalized;
    }

    private String normalizeLicenseClass(String licenseClass) {
        String normalized = licenseClass.strip().toUpperCase(Locale.ROOT);
        if (!LICENSE_CLASSES.contains(normalized)) {
            throw validationError("Hạng giấy phép lái xe chỉ chấp nhận B1, B2, C hoặc D.");
        }
        return normalized;
    }

    private LocalDate validateLicenseExpiryDate(LocalDate licenseExpiryDate) {
        LocalDate minimumExpiryDate = LocalDate.now(VIETNAM_ZONE).plusDays(90);
        if (licenseExpiryDate.isBefore(minimumExpiryDate)) {
            throw validationError("Ngày hết hạn giấy phép lái xe phải còn hiệu lực ít nhất 90 ngày.");
        }
        return licenseExpiryDate;
    }

    private String normalizeVehiclePlate(String vehiclePlate) {
        String normalized = vehiclePlate.strip()
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (!VEHICLE_PLATE_PATTERN.matcher(normalized).matches()) {
            throw validationError("Biển số xe không hợp lệ. Ví dụ hợp lệ: 30E-56789.");
        }
        return normalized;
    }

    private String normalizeVehicleType(String vehicleType) {
        String normalized = vehicleType.strip().toUpperCase(Locale.ROOT);
        if (!VEHICLE_TYPES.contains(normalized)) {
            throw validationError("Loại xe chỉ chấp nhận TRUCK_500KG, TRUCK_1T hoặc TRUCK_15T.");
        }
        return normalized;
    }

    private Integer validateVehicleCapacityKg(Integer vehicleCapacityKg) {
        if (vehicleCapacityKg == null || vehicleCapacityKg < 1) {
            throw validationError("Tải trọng xe phải lớn hơn 0 kg.");
        }
        return vehicleCapacityKg;
    }

    private void validateProfileCompleteForSubmit(DriverProfile profile) {
        List<String> missingFields = new ArrayList<>();
        if (isBlank(profile.getLicenseNumber())) {
            missingFields.add("licenseNumber");
        }
        if (isBlank(profile.getLicenseClass())) {
            missingFields.add("licenseClass");
        }
        if (profile.getLicenseExpiryDate() == null) {
            missingFields.add("licenseExpiryDate");
        }
        if (isBlank(profile.getVehiclePlate())) {
            missingFields.add("vehiclePlate");
        }
        if (isBlank(profile.getVehicleType())) {
            missingFields.add("vehicleType");
        }
        if (profile.getVehicleCapacityKg() == null) {
            missingFields.add("vehicleCapacityKg");
        }

        if (!missingFields.isEmpty()) {
            throw validationError("Hồ sơ chưa đủ thông tin bắt buộc: "
                    + String.join(", ", missingFields) + ".");
        }

        normalizeLicenseNumber(profile.getLicenseNumber());
        normalizeLicenseClass(profile.getLicenseClass());
        validateLicenseExpiryDate(profile.getLicenseExpiryDate());
        normalizeVehiclePlate(profile.getVehiclePlate());
        normalizeVehicleType(profile.getVehicleType());
        validateVehicleCapacityKg(profile.getVehicleCapacityKg());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException validationError(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR|" + message);
    }

    private ResponseStatusException invalidOnboardingStep(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "INVALID_ONBOARDING_STEP|" + message);
    }

    private ResponseStatusException optimisticLockConflict(OptimisticLockingFailureException ex) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "INVALID_ONBOARDING_STEP|Hồ sơ vừa được cập nhật ở nơi khác, thử lại.", ex);
    }

    private DriverProfileResponse toResponse(DriverProfile profile) {
        return new DriverProfileResponse(
                profile.getLicenseNumber(),
                profile.getLicenseClass(),
                profile.getLicenseExpiryDate(),
                profile.getVehiclePlate(),
                profile.getVehicleType(),
                profile.getVehicleCapacityKg(),
                profile.getApprovedAt(),
                profile.getTotalOrdersCompleted(),
                profile.getAverageRating(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                onboardingStatus(profile)
        );
    }

    private String onboardingStatus(DriverProfile profile) {
        if (profile.getApprovedAt() != null) {
            return "DA_DUYET";
        }
        if (profile.getOnboardingCompletedAt() != null) {
            return "CHO_DUYET";
        }
        return "CHUA_NOP";
    }
}
