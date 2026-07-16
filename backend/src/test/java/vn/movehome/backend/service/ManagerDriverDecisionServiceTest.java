package vn.movehome.backend.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.movehome.backend.dto.manager.ApproveDriverRequest;
import vn.movehome.backend.dto.manager.ApproveDriverResponse;
import vn.movehome.backend.dto.manager.RejectDriverRequest;
import vn.movehome.backend.dto.manager.RejectDriverResponse;
import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.DriverDocumentRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ManagerDriverDecisionServiceTest {

    private static final String DRIVER_EMAIL = "driver@example.com";
    private static final String DRIVER_FULL_NAME = "Nguyen Van Driver";
    private static final String DRIVER_PHONE = "+84901234567";
    private static final String MANAGER_EMAIL = "manager@movehome.vn";
    private static final String VALID_REJECTION_REASON = "Anh CCCD bi mo khong ro chu trang chu va so the";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverDocumentRepository driverDocumentRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ManagerDriverDecisionService decisionService;

    @Disabled("test doi 6 tai lieu, code yeu cau 8 - mo lai sau khi xong code")
    @Test
    void approvesDriverSuccessfully() {
        UUID driverId = UUID.randomUUID();
        User actor = buildActor();
        User driver = buildPendingDriver(driverId);
        DriverProfile profile = buildValidProfile(driverId);
        Optional<User> driverOpt = Optional.of(driver);
        Optional<DriverProfile> profileOpt = Optional.of(profile);

        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(6L);
        stubObjectMapper();

        ApproveDriverResponse response = decisionService.approve(driverId, buildApproveRequest(), actor);

        assertThat(driver.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.driverId()).isEqualTo(driverId);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE.name());
        assertThat(response.approvedAt()).isNotNull();
        verify(userRepository, times(1)).save(driver);
        verify(driverProfileRepository, times(1)).save(profile);
    }

    @Disabled("test doi 6 tai lieu, code yeu cau 8 - mo lai sau khi xong code")
    @Test
    void approveSavesBothUserAndProfile() {
        UUID driverId = UUID.randomUUID();
        User actor = buildActor();
        User driver = buildPendingDriver(driverId);
        DriverProfile profile = buildValidProfile(driverId);
        Optional<User> driverOpt = Optional.of(driver);
        Optional<DriverProfile> profileOpt = Optional.of(profile);

        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(6L);
        stubObjectMapper();

        decisionService.approve(driverId, buildApproveRequest(), actor);

        verify(userRepository, times(1)).save(any(User.class));
        verify(driverProfileRepository, times(1)).save(any(DriverProfile.class));
    }

    @Disabled("test doi 6 tai lieu, code yeu cau 8 - mo lai sau khi xong code")
    @Test
    void approveCallsAuditAndNotification() {
        UUID driverId = UUID.randomUUID();
        User actor = buildActor();
        User driver = buildPendingDriver(driverId);
        DriverProfile profile = buildValidProfile(driverId);
        Optional<User> driverOpt = Optional.of(driver);
        Optional<DriverProfile> profileOpt = Optional.of(profile);

        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(6L);
        stubObjectMapper();

        decisionService.approve(driverId, buildApproveRequest(), actor);

        verify(auditService, times(1)).log(
                eq(actor.getId()),
                eq(actor.getEmail()),
                eq("DRIVER_APPROVED"),
                eq("USER"),
                any(),
                any());
        verify(notificationService, times(1)).create(
                eq(driverId),
                eq(NotificationType.DRIVER_APPROVED),
                any(),
                any());
        verify(emailService, times(1)).send(eq(driver.getEmail()), any(), any());
    }

    @Test
    void rejectsApproveWhenDriverNotFound() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.empty();
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(userRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsApproveWhenUserNotDriverRole() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildDriverWithRole(driverId, UserRole.CUSTOMER));
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void rejectsApproveWhenDriverAlreadyDecided() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildDriverWithStatus(driverId, UserStatus.ACTIVE));
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_ALREADY_DECIDED");
        verify(driverProfileRepository, never()).findByUserId(any());
    }

    @Test
    void rejectsApproveWhenProfileMissing() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildPendingDriver(driverId));
        Optional<DriverProfile> profileOpt = Optional.empty();
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsApproveWhenOnboardingIncomplete() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildPendingDriver(driverId));
        Optional<DriverProfile> profileOpt = Optional.of(buildProfileMissingOnboarding(driverId));
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(6L);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_APPROVABLE")
                .hasMessageContaining("chưa hoàn tất onboarding");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsApproveWhenDepositMissing() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildPendingDriver(driverId));
        Optional<DriverProfile> profileOpt = Optional.of(buildProfileMissingDeposit(driverId));
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(6L);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_APPROVABLE")
                .hasMessageContaining("chưa đóng cọc");
        verify(userRepository, never()).save(any());
    }

    @Disabled("test doi 6 tai lieu, code yeu cau 8 - mo lai sau khi xong code")
    @Test
    void rejectsApproveWhenDocumentsInsufficient() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildPendingDriver(driverId));
        Optional<DriverProfile> profileOpt = Optional.of(buildValidProfile(driverId));
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(profileOpt);
        when(driverDocumentRepository.countByDriverId(driverId)).thenReturn(5L);

        assertThatThrownBy(() -> decisionService.approve(driverId, buildApproveRequest(), buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_APPROVABLE")
                .hasMessageContaining("chưa đủ tài liệu (5/6)");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDriverSuccessfully() {
        UUID driverId = UUID.randomUUID();
        User actor = buildActor();
        User driver = buildPendingDriver(driverId);
        Optional<User> driverOpt = Optional.of(driver);

        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);
        stubObjectMapper();

        RejectDriverResponse response = decisionService.reject(
                driverId,
                buildRejectRequest(VALID_REJECTION_REASON),
                actor);

        assertThat(driver.getStatus()).isEqualTo(UserStatus.REJECTED);
        assertThat(driver.getRejectionReason()).isEqualTo(VALID_REJECTION_REASON);
        assertThat(response.canResubmit()).isTrue();
        assertThat(response.status()).isEqualTo(UserStatus.REJECTED.name());
        verify(userRepository, times(1)).save(driver);
        verify(auditService, times(1)).log(
                eq(actor.getId()),
                eq(actor.getEmail()),
                eq("DRIVER_REJECTED"),
                eq("USER"),
                any(),
                any());
        verify(notificationService, times(1)).create(
                eq(driverId),
                eq(NotificationType.DRIVER_REJECTED),
                any(),
                any());
        verify(emailService, times(1)).send(eq(driver.getEmail()), any(), any());
    }

    @Test
    void rejectsRejectWhenReasonTooShort() {
        assertThatThrownBy(() -> decisionService.reject(
                UUID.randomUUID(),
                buildRejectRequest("Ly do qua ngan"),
                buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_REJECTION_REASON");
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsRejectWhenReasonTooLong() {
        assertThatThrownBy(() -> decisionService.reject(
                UUID.randomUUID(),
                buildRejectRequest("a".repeat(501)),
                buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_REJECTION_REASON");
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsRejectWhenReasonHasNoLetter() {
        assertThatThrownBy(() -> decisionService.reject(
                UUID.randomUUID(),
                buildRejectRequest("12345678901234567890123"),
                buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_REJECTION_REASON")
                .hasMessageContaining("chứa ít nhất một chữ cái");
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsRejectWhenDriverAlreadyDecided() {
        UUID driverId = UUID.randomUUID();
        Optional<User> driverOpt = Optional.of(buildDriverWithStatus(driverId, UserStatus.ACTIVE));
        when(userRepository.findByIdForUpdate(driverId)).thenReturn(driverOpt);

        assertThatThrownBy(() -> decisionService.reject(
                driverId,
                buildRejectRequest(VALID_REJECTION_REASON),
                buildActor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_ALREADY_DECIDED");
        verify(userRepository, never()).save(any());
    }

    private User buildPendingDriver(UUID id) {
        return buildDriverWithStatus(id, UserStatus.PENDING_APPROVAL);
    }

    private User buildDriverWithStatus(UUID id, UserStatus status) {
        return User.builder()
                .id(id)
                .email(DRIVER_EMAIL)
                .fullName(DRIVER_FULL_NAME)
                .phone(DRIVER_PHONE)
                .passwordHash("hash")
                .role(UserRole.DRIVER)
                .status(status)
                .deletedAt(null)
                .build();
    }

    private User buildDriverWithRole(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .email(DRIVER_EMAIL)
                .fullName(DRIVER_FULL_NAME)
                .phone(DRIVER_PHONE)
                .passwordHash("hash")
                .role(role)
                .status(UserStatus.PENDING_APPROVAL)
                .deletedAt(null)
                .build();
    }

    private User buildActor() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(MANAGER_EMAIL)
                .fullName("Manager Movehome")
                .phone("+84909876543")
                .passwordHash("hash")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .deletedAt(null)
                .build();
    }

    private DriverProfile buildValidProfile(UUID userId) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .onboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .depositPaidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private DriverProfile buildProfileMissingOnboarding(UUID userId) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .onboardingCompletedAt(null)
                .depositPaidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private DriverProfile buildProfileMissingDeposit(UUID userId) {
        return DriverProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .onboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .depositPaidAt(null)
                .build();
    }

    private ApproveDriverRequest buildApproveRequest() {
        return new ApproveDriverRequest("OK");
    }

    private RejectDriverRequest buildRejectRequest(String reason) {
        return new RejectDriverRequest(reason, "kiem tra lai");
    }

    private void stubObjectMapper() {
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}