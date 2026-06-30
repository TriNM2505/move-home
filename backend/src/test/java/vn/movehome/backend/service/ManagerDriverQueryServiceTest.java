package vn.movehome.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.dto.manager.DriverDetailResponse;
import vn.movehome.backend.dto.manager.DriverDocumentItem;
import vn.movehome.backend.dto.manager.PendingDriverItem;
import vn.movehome.backend.dto.manager.RejectedDriverItem;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.DriverDocumentRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ManagerDriverQueryServiceTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String FULL_NAME = "Nguyen Van Driver";
    private static final String EMAIL = "driver@example.com";
    private static final String PHONE = "+84901234567";
    private static final String LICENSE_NUMBER = "B123456789";
    private static final String LICENSE_CLASS = "B2";
    private static final String VEHICLE_PLATE = "30E-56789";
    private static final String VEHICLE_TYPE = "TRUCK_500KG";
    private static final Integer VEHICLE_CAPACITY_KG = 500;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverDocumentRepository driverDocumentRepository;

    @InjectMocks
    private ManagerDriverQueryService queryService;

    @Test
    void returnsPagedPendingDrivers() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildProfile(driverId);
        User user = buildDriver(driverId);
        Page<DriverProfile> profiles = new PageImpl<>(List.of(profile));
        Optional<User> userOpt = Optional.of(user);

        when(driverProfileRepository.findPendingApproval(any(Pageable.class))).thenReturn(profiles);
        when(userRepository.findById(driverId)).thenReturn(userOpt);
        when(driverDocumentRepository.countByDriverId(any())).thenReturn(2L);

        Page<PendingDriverItem> page = queryService.findPendingApproval(0, 10);

        assertThat(page.getContent()).hasSize(1);
        PendingDriverItem item = page.getContent().get(0);
        assertThat(item.fullName()).isEqualTo(user.getFullName());
        assertThat(item.email()).isEqualTo(user.getEmail());
        assertThat(item.phone()).isEqualTo(user.getPhone());
        assertThat(item.documentCount()).isEqualTo(2);
    }

    @Test
    void returnsPendingItemWithNullFieldsWhenUserMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildProfile(driverId);
        Page<DriverProfile> profiles = new PageImpl<>(List.of(profile));
        Optional<User> userOpt = Optional.empty();

        when(driverProfileRepository.findPendingApproval(any(Pageable.class))).thenReturn(profiles);
        when(userRepository.findById(driverId)).thenReturn(userOpt);
        when(driverDocumentRepository.countByDriverId(any())).thenReturn(0L);

        Page<PendingDriverItem> page = queryService.findPendingApproval(0, 10);

        PendingDriverItem item = page.getContent().get(0);
        assertThat(item.fullName()).isNull();
        assertThat(item.email()).isNull();
        assertThat(item.phone()).isNull();
        assertThat(item.documentCount()).isZero();
    }

    @Test
    void clampsInvalidPageAndSize() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(driverProfileRepository.findPendingApproval(any(Pageable.class))).thenReturn(Page.empty());

        queryService.findPendingApproval(-5, 200);

        verify(driverProfileRepository, times(1)).findPendingApproval(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void returnsPagedRejectedDrivers() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildProfile(driverId);
        User user = buildDriver(driverId);
        user.setRejectionReason("Anh CCCD bi mo");
        user.setUpdatedAt(Instant.now());
        Page<DriverProfile> profiles = new PageImpl<>(List.of(profile));
        Optional<User> userOpt = Optional.of(user);

        when(driverProfileRepository.findRejected(any(Pageable.class))).thenReturn(profiles);
        when(userRepository.findById(driverId)).thenReturn(userOpt);

        Page<RejectedDriverItem> page = queryService.findRejected(0, 10);

        RejectedDriverItem item = page.getContent().get(0);
        assertThat(item.rejectionReason()).isEqualTo("Anh CCCD bi mo");
        assertThat(item.updatedAt()).isNotNull();
        assertThat(item.updatedAt()).isInstanceOf(OffsetDateTime.class);
    }

    @Test
    void returnsRejectedItemWithNullFieldsWhenUserMissing() {
        UUID driverId = UUID.randomUUID();
        DriverProfile profile = buildProfile(driverId);
        Page<DriverProfile> profiles = new PageImpl<>(List.of(profile));
        Optional<User> userOpt = Optional.empty();

        when(driverProfileRepository.findRejected(any(Pageable.class))).thenReturn(profiles);
        when(userRepository.findById(driverId)).thenReturn(userOpt);

        Page<RejectedDriverItem> page = queryService.findRejected(0, 10);

        RejectedDriverItem item = page.getContent().get(0);
        assertThat(item.fullName()).isNull();
        assertThat(item.email()).isNull();
        assertThat(item.phone()).isNull();
        assertThat(item.rejectionReason()).isNull();
        assertThat(item.updatedAt()).isNull();
    }

    @Test
    void returnsDetailWhenDriverExists() {
        UUID driverId = UUID.randomUUID();
        User user = buildDriver(driverId);
        DriverProfile profile = buildProfile(driverId);
        Optional<User> userOpt = Optional.of(user);
        Optional<DriverProfile> profileOpt = Optional.of(profile);

        when(userRepository.findById(driverId)).thenReturn(userOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(3L);

        DriverDetailResponse response = queryService.getDetail(driverId);

        assertThat(response.driverId()).isEqualTo(driverId);
        assertThat(response.fullName()).isEqualTo(user.getFullName());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.phone()).isEqualTo(user.getPhone());
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE.name());
        assertThat(response.licenseNumber()).isEqualTo(profile.getLicenseNumber());
        assertThat(response.licenseClass()).isEqualTo(profile.getLicenseClass());
        assertThat(response.licenseExpiryDate()).isEqualTo(profile.getLicenseExpiryDate());
        assertThat(response.vehiclePlate()).isEqualTo(profile.getVehiclePlate());
        assertThat(response.vehicleType()).isEqualTo(profile.getVehicleType());
        assertThat(response.vehicleCapacityKg()).isEqualTo(profile.getVehicleCapacityKg());
        assertThat(response.documentCount()).isEqualTo(3);
    }

    @Test
    void throwsNotFoundWhenUserMissing() {
        UUID driverId = UUID.randomUUID();
        Optional<User> userOpt = Optional.empty();
        when(userRepository.findById(driverId)).thenReturn(userOpt);

        assertThatThrownBy(() -> queryService.getDetail(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void throwsNotFoundWhenUserIsNotDriver() {
        UUID driverId = UUID.randomUUID();
        Optional<User> userOpt = Optional.of(buildDriverWithRole(driverId, UserRole.CUSTOMER));
        when(userRepository.findById(driverId)).thenReturn(userOpt);

        assertThatThrownBy(() -> queryService.getDetail(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void throwsNotFoundWhenUserSoftDeleted() {
        UUID driverId = UUID.randomUUID();
        User user = buildDriver(driverId);
        user.setDeletedAt(Instant.now());
        Optional<User> userOpt = Optional.of(user);
        when(userRepository.findById(driverId)).thenReturn(userOpt);

        assertThatThrownBy(() -> queryService.getDetail(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void returnsDocumentsListWhenDriverExists() {
        UUID driverId = UUID.randomUUID();
        Optional<User> userOpt = Optional.of(buildDriver(driverId));
        DriverDocument drivingLicense = buildDocument("DRIVING_LICENSE");
        DriverDocument vehicleRegistration = buildDocument("VEHICLE_REGISTRATION");
        List<DriverDocument> documents = List.of(drivingLicense, vehicleRegistration);

        when(userRepository.findById(driverId)).thenReturn(userOpt);
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(eq(driverId))).thenReturn(documents);

        List<DriverDocumentItem> result = queryService.getDocuments(driverId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).docType()).isEqualTo("DRIVING_LICENSE");
    }

    @Test
    void returnsEmptyListWhenDriverHasNoDocuments() {
        UUID driverId = UUID.randomUUID();
        Optional<User> userOpt = Optional.of(buildDriver(driverId));
        List<DriverDocument> documents = List.of();

        when(userRepository.findById(driverId)).thenReturn(userOpt);
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(eq(driverId))).thenReturn(documents);

        List<DriverDocumentItem> result = queryService.getDocuments(driverId);

        assertThat(result).isEmpty();
    }

    @Test
    void throwsNotFoundWhenLoadDriverFails() {
        UUID driverId = UUID.randomUUID();
        Optional<User> userOpt = Optional.of(buildDriverWithRole(driverId, UserRole.CUSTOMER));
        when(userRepository.findById(driverId)).thenReturn(userOpt);

        assertThatThrownBy(() -> queryService.getDocuments(driverId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(driverDocumentRepository, never()).findByDriverIdOrderByUploadedAtDesc(any());
    }

    private User buildDriver(UUID id) {
        return buildDriverWithRole(id, UserRole.DRIVER);
    }

    private User buildDriverWithRole(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .fullName(FULL_NAME)
                .email(EMAIL)
                .phone(PHONE)
                .passwordHash("hash")
                .role(role)
                .status(UserStatus.ACTIVE)
                .deletedAt(null)
                .updatedAt(Instant.now())
                .build();
    }

    private DriverProfile buildProfile(UUID userId) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .licenseNumber(LICENSE_NUMBER)
                .licenseClass(LICENSE_CLASS)
                .licenseExpiryDate(LocalDate.now(VIETNAM_ZONE).plusDays(100))
                .vehiclePlate(VEHICLE_PLATE)
                .vehicleType(VEHICLE_TYPE)
                .vehicleCapacityKg(VEHICLE_CAPACITY_KG)
                .depositAmount(new BigDecimal("3000000"))
                .depositPaidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .onboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .approvedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .approvedByManagerId(UUID.randomUUID())
                .build();
    }

    private DriverDocument buildDocument(String docType) {
        String url = switch (docType) {
            case "DRIVING_LICENSE" -> "https://cdn/dl.jpg";
            case "VEHICLE_REGISTRATION" -> "https://cdn/vr.jpg";
            default -> "https://cdn/document.jpg";
        };

        return DriverDocument.builder()
                .id(UUID.randomUUID())
                .docType(docType)
                .url(url)
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
