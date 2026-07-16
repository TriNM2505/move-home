package vn.movehome.backend.service;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Disabled("test doi 6 tai lieu, code yeu cau 8 - mo lai sau khi xong code")
    @Test
    void submitsProfileSuccessfullyWhenCompleteAndDocumentsExist() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildCompleteProfile(driverId);
        Optional<DriverProfile> profileOpt = Optional.of(profile);
        List<DriverDocument> documents = List.of(
                buildDocument("DRIVING_LICENSE"),
                buildDocument("VEHICLE_REGISTRATION"),
                buildDocument("VEHICLE_PHOTO"));

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