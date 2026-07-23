package vn.movehome.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.dto.driver.onboarding.DriverProfileResponse;
import vn.movehome.backend.dto.driver.onboarding.UpdateDriverProfileRequest;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.DriverDocumentRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String LICENSE_NUMBER = "B123456789";
    private static final String LICENSE_CLASS = "B2";
    private static final String VEHICLE_PLATE = "30E-56789";
    private static final String VEHICLE_TYPE = "TRUCK_500KG";
    private static final Integer VEHICLE_CAPACITY_KG = 500;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverDocumentRepository driverDocumentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DriverProfileService driverProfileService;

    @Test
    void returnsResponseWhenProfileExists() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        DriverProfileResponse response = driverProfileService.getProfile(driverId);

        assertThat(response.onboardingStatus()).isEqualTo("CHUA_NOP");
    }

    @Test
    void throwsNotFoundWhenProfileMissing() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.empty();
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.getProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ONBOARDING_PROFILE_NOT_FOUND");
    }

    @Test
    void updatesProfileWithValidData() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildEmptyProfile(driverId);
        Optional<DriverProfile> profileOpt = Optional.of(profile);
        UpdateDriverProfileRequest request = buildValidRequest();

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(false);
        when(driverProfileRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DriverProfileResponse response = driverProfileService.updateProfile(driverId, request);

        assertThat(response.licenseNumber()).isEqualTo(request.licenseNumber());
        assertThat(response.licenseClass()).isEqualTo(request.licenseClass());
        assertThat(response.licenseExpiryDate()).isEqualTo(request.licenseExpiryDate());
        assertThat(response.vehiclePlate()).isEqualTo(request.vehiclePlate());
        assertThat(response.vehicleType()).isEqualTo(request.vehicleType());
        assertThat(response.vehicleCapacityKg()).isEqualTo(request.vehicleCapacityKg());
        verify(driverProfileRepository, times(1)).saveAndFlush(profile);
    }

    @Test
    void rejectsUnknownFieldsInRequest() {
        UpdateDriverProfileRequest request = buildValidRequest();
        addUnknownField(request, "driverId");

        assertThatThrownBy(() -> driverProfileService.updateProfile(UUID.randomUUID(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("IMMUTABLE_FIELD");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void rejectsUpdateWhenProfileAlreadyApproved() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildEmptyProfile(driverId);
        profile.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Optional<DriverProfile> profileOpt = Optional.of(profile);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_ONBOARDING_STEP")
                .hasMessageContaining("đã được duyệt");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUpdateWhenProfileAlreadySubmitted() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildEmptyProfile(driverId);
        profile.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Optional<DriverProfile> profileOpt = Optional.of(profile);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_ONBOARDING_STEP")
                .hasMessageContaining("đang chờ duyệt");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateVehiclePlate() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(true);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LICENSE_PLATE_ALREADY_USED");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidLicenseNumber() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setLicenseNumber("abc");

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExpiringLicense() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setLicenseExpiryDate(LocalDate.now(VIETNAM_ZONE).plusDays(30));

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapsOptimisticLockToConflict() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(false);
        when(driverProfileRepository.saveAndFlush(any()))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_ONBOARDING_STEP");
    }

    @Test
    void mapsDataIntegrityViolationToVehiclePlateConflict() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(false);
        when(driverProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "constraint uq_driver_profile_vehicle_plate violated"));

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LICENSE_PLATE_ALREADY_USED");
    }

    @Test
    void submitsProfileSuccessfullyWhenCompleteAndDocumentsExist() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        Optional<DriverProfile> profileOpt = Optional.of(profile);
        List<DriverDocument> documents = List.of(
                buildDocument("DRIVING_LICENSE_FRONT"),
                buildDocument("DRIVING_LICENSE_BACK"),
                buildDocument("VEHICLE_REGISTRATION_FRONT"),
                buildDocument("VEHICLE_REGISTRATION_BACK"),
                buildDocument("VEHICLE_PHOTO_FRONT"),
                buildDocument("VEHICLE_PHOTO_REAR"),
                buildDocument("VEHICLE_PHOTO_SIDE"),
                buildDocument("FACE_PHOTO"));

        User driver = User.builder().id(driverId).status(UserStatus.PENDING_DOCUMENTS).build();
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)).thenReturn(documents);
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DriverProfileResponse response = driverProfileService.submitProfile(driverId);

        assertThat(response.onboardingStatus()).isEqualTo("CHO_DUYET");
        assertThat(driver.getStatus()).isEqualTo(UserStatus.PENDING_DEPOSIT);
        verify(driverProfileRepository, times(1)).saveAndFlush(profile);
    }

    @Test
    void rejectsSubmitWhenProfileIncomplete() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setLicenseNumber(null);
        Optional<DriverProfile> profileOpt = Optional.of(profile);

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("licenseNumber");
        verify(driverDocumentRepository, never()).findByDriverIdOrderByUploadedAtDesc(any());
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSubmitWhenDocumentsIncomplete() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildCompleteProfile(driverId));
        List<DriverDocument> documents = List.of(
                buildDocument("DRIVING_LICENSE"),
                buildDocument("VEHICLE_REGISTRATION"));

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)).thenReturn(documents);

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ONBOARDING_DOCUMENTS_INCOMPLETE");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsApprovedStatusWhenProfileApproved() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        DriverProfileResponse response = driverProfileService.getProfile(driverId);

        assertThat(response.onboardingStatus()).isEqualTo("DA_DUYET");
    }

    @Test
    void rejectsInvalidLicenseClass() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setLicenseClass("A1");

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("Hạng giấy phép lái xe chỉ chấp nhận B1, B2, C hoặc D.");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidVehiclePlate() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setVehiclePlate("KHONG-HOP-LE");

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("Biển số xe không hợp lệ. Ví dụ hợp lệ: 30E-56789.");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidVehicleType() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setVehicleType("MOTORBIKE");

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("Loại xe chỉ chấp nhận TRUCK_500KG, TRUCK_1T hoặc TRUCK_15T.");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNullVehicleCapacityKg() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setVehicleCapacityKg(null);

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("Tải trọng xe phải lớn hơn 0 kg.");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNegativeVehicleCapacityKg() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        UpdateDriverProfileRequest request = buildValidRequest();
        request.setVehicleCapacityKg(0);

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("Tải trọng xe phải lớn hơn 0 kg.");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapsDataIntegrityViolationToLicenseNumberConflict() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(false);
        when(driverProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates constraint license_number_key"));

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LICENSE_NUMBER_ALREADY_USED");
    }

    @Test
    void mapsDataIntegrityViolationToVehiclePlateConflictViaColumnNameOnly() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(false);
        when(driverProfileRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint vehicle_plate_key"));

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LICENSE_PLATE_ALREADY_USED");
    }

    @Test
    void rethrowsDataIntegrityViolationWhenConstraintUnrecognized() {
        UUID driverId = UUID.randomUUID();
        Optional<DriverProfile> profileOpt = Optional.of(buildEmptyProfile(driverId));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverProfileRepository.existsByVehiclePlateAndUserIdNot(VEHICLE_PLATE, driverId)).thenReturn(false);
        DataIntegrityViolationException original = new DataIntegrityViolationException("constraint khong xac dinh");
        when(driverProfileRepository.saveAndFlush(any())).thenThrow(original);

        assertThatThrownBy(() -> driverProfileService.updateProfile(driverId, buildValidRequest()))
                .isSameAs(original);
    }

    @Test
    void rejectsSubmitWhenAlreadyApproved() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_ONBOARDING_STEP")
                .hasMessageContaining("đã được duyệt, không thể nộp lại");
        verify(driverDocumentRepository, never()).findByDriverIdOrderByUploadedAtDesc(any());
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSubmitWhenAlreadySubmitted() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_ONBOARDING_STEP")
                .hasMessageContaining("đã được nộp và đang chờ duyệt");
        verify(driverDocumentRepository, never()).findByDriverIdOrderByUploadedAtDesc(any());
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitProfileThrowsWhenDriverUserMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        List<DriverDocument> documents = allRequiredDocuments();

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)).thenReturn(documents);
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NOT_FOUND")
                .hasMessageContaining("Tai khoan tai xe khong ton tai");
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitProfileKeepsStatusWhenNotPendingDocuments() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        List<DriverDocument> documents = allRequiredDocuments();
        User driver = User.builder().id(driverId).status(UserStatus.PENDING_DEPOSIT).build();

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)).thenReturn(documents);
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        driverProfileService.submitProfile(driverId);

        assertThat(driver.getStatus()).isEqualTo(UserStatus.PENDING_DEPOSIT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void submitProfileMapsOptimisticLockConflict() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        List<DriverDocument> documents = allRequiredDocuments();
        User driver = User.builder().id(driverId).status(UserStatus.PENDING_DOCUMENTS).build();

        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)).thenReturn(documents);
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.saveAndFlush(any()))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_ONBOARDING_STEP");
    }

    @Test
    void rejectsSubmitWhenLicenseClassMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setLicenseClass(null);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("licenseClass");
    }

    @Test
    void rejectsSubmitWhenLicenseExpiryDateMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setLicenseExpiryDate(null);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("licenseExpiryDate");
    }

    @Test
    void rejectsSubmitWhenVehiclePlateMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setVehiclePlate(null);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("vehiclePlate");
    }

    @Test
    void rejectsSubmitWhenVehicleTypeMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setVehicleType(null);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("vehicleType");
    }

    @Test
    void rejectsSubmitWhenVehicleCapacityMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        profile.setVehicleCapacityKg(null);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> driverProfileService.submitProfile(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VALIDATION_ERROR")
                .hasMessageContaining("vehicleCapacityKg");
    }

    private List<DriverDocument> allRequiredDocuments() {
        return List.of(
                buildDocument("DRIVING_LICENSE_FRONT"),
                buildDocument("DRIVING_LICENSE_BACK"),
                buildDocument("VEHICLE_REGISTRATION_FRONT"),
                buildDocument("VEHICLE_REGISTRATION_BACK"),
                buildDocument("VEHICLE_PHOTO_FRONT"),
                buildDocument("VEHICLE_PHOTO_REAR"),
                buildDocument("VEHICLE_PHOTO_SIDE"),
                buildDocument("FACE_PHOTO"));
    }

    private DriverProfile buildEmptyProfile(UUID userId) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .build();
    }

    private DriverProfile buildCompleteProfile(UUID userId) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .licenseNumber(LICENSE_NUMBER)
                .licenseClass(LICENSE_CLASS)
                .licenseExpiryDate(validLicenseExpiryDate())
                .vehiclePlate(VEHICLE_PLATE)
                .vehicleType(VEHICLE_TYPE)
                .vehicleCapacityKg(VEHICLE_CAPACITY_KG)
                .build();
    }

    private UpdateDriverProfileRequest buildValidRequest() {
        UpdateDriverProfileRequest request = new UpdateDriverProfileRequest();
        request.setLicenseNumber(LICENSE_NUMBER);
        request.setLicenseClass(LICENSE_CLASS);
        request.setLicenseExpiryDate(validLicenseExpiryDate());
        request.setVehiclePlate(VEHICLE_PLATE);
        request.setVehicleType(VEHICLE_TYPE);
        request.setVehicleCapacityKg(VEHICLE_CAPACITY_KG);
        return request;
    }

    private DriverDocument buildDocument(String docType) {
        return DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(UUID.randomUUID())
                .docType(docType)
                .url("https://example.com/document.jpg")
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private LocalDate validLicenseExpiryDate() {
        return LocalDate.now(VIETNAM_ZONE).plusDays(100);
    }

    private void addUnknownField(UpdateDriverProfileRequest request, String fieldName) {
        try {
            Method method = UpdateDriverProfileRequest.class.getDeclaredMethod(
                    "setUnknownField",
                    String.class,
                    Object.class);
            method.setAccessible(true);
            method.invoke(request, fieldName, "ignored");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
