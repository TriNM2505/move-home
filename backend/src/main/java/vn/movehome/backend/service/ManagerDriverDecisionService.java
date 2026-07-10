package vn.movehome.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerDriverDecisionService {

    // Onboarding thu 1 anh/loai cho 8 loai bat buoc (khop DriverProfileService.REQUIRED_DOCUMENT_TYPES):
    // GPLX truoc/sau, dang ky xe truoc/sau, anh xe truoc/sau/ngang, anh chan dung.
    private static final int REQUIRED_DOCUMENT_COUNT = 8;
    private static final int REASON_MIN_LENGTH = 20;
    private static final int REASON_MAX_LENGTH = 500;

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApproveDriverResponse approve(UUID driverId, ApproveDriverRequest request, User actor) {
        User driver = userRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));

        validateDriverPendingApproval(driver);

        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));

        checkApprovalEligibility(profile, driverId);

        UserStatus previousStatus = driver.getStatus();
        OffsetDateTime approvedAt = OffsetDateTime.now(ZoneOffset.UTC);

        driver.setStatus(UserStatus.ACTIVE);
        profile.setApprovedAt(approvedAt);
        profile.setApprovedByManagerId(actor.getId());
        userRepository.save(driver);
        driverProfileRepository.save(profile);

        String decisionNote = request != null ? request.decisionNote() : null;
        String detail = buildApproveDetail(previousStatus, decisionNote);
        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "DRIVER_APPROVED",
                "USER",
                driverId.toString(),
                detail);

        notifyAndEmail(
                driver,
                NotificationType.DRIVER_APPROVED,
                "Tài khoản đã được duyệt",
                "Hồ sơ tài xế của bạn đã được Move_home duyệt thành công.");

        return new ApproveDriverResponse(
                driverId,
                UserStatus.ACTIVE.name(),
                "Đã duyệt tài xế thành công",
                approvedAt);
    }

    @Transactional
    public RejectDriverResponse reject(UUID driverId, RejectDriverRequest request, User actor) {
        String reason = validateRejectionReason(request);
        String decisionNote = request != null ? request.decisionNote() : null;

        User driver = userRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));

        validateDriverPendingApproval(driver);

        UserStatus previousStatus = driver.getStatus();

        driver.setStatus(UserStatus.REJECTED);
        driver.setRejectionReason(reason);
        userRepository.save(driver);

        String detail = buildRejectDetail(previousStatus, reason, decisionNote);
        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "DRIVER_REJECTED",
                "USER",
                driverId.toString(),
                detail);

        notifyAndEmail(
                driver,
                NotificationType.DRIVER_REJECTED,
                "Hồ sơ tài xế chưa được duyệt",
                "Hồ sơ tài xế của bạn chưa được duyệt. Lý do: " + reason);

        return new RejectDriverResponse(
                driverId,
                UserStatus.REJECTED.name(),
                "Đã từ chối hồ sơ tài xế",
                true);
    }

    private void validateDriverPendingApproval(User driver) {
        if (driver.getRole() != UserRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DRIVER_NOT_FOUND|Không tìm thấy tài xế.");
        }

        if (driver.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DRIVER_NOT_FOUND|Không tìm thấy tài xế.");
        }

        if (driver.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DRIVER_ALREADY_DECIDED|Hồ sơ đã được xử lý.");
        }
    }

    private void checkApprovalEligibility(DriverProfile profile, UUID driverId) {
        List<String> missing = new ArrayList<>();

        if (profile.getOnboardingCompletedAt() == null) {
            missing.add("chưa hoàn tất onboarding");
        }
        if (profile.getDepositPaidAt() == null) {
            missing.add("chưa đóng cọc");
        }

        long documentCount = driverDocumentRepository.countByDriverId(driverId);
        if (documentCount < REQUIRED_DOCUMENT_COUNT) {
            missing.add("chưa đủ tài liệu (" + documentCount + "/" + REQUIRED_DOCUMENT_COUNT + ")");
        }

        if (!missing.isEmpty()) {
            String detail = String.join(", ", missing);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "DRIVER_NOT_APPROVABLE|Hồ sơ chưa đủ điều kiện duyệt: " + detail + ".");
        }
    }

    private String validateRejectionReason(RejectDriverRequest request) {
        if (request == null || request.reason() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_REJECTION_REASON|Lý do từ chối phải từ 20 đến 500 ký tự.");
        }

        String trimmed = request.reason().trim();
        int length = trimmed.length();
        if (length < REASON_MIN_LENGTH || length > REASON_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_REJECTION_REASON|Lý do từ chối phải từ 20 đến 500 ký tự.");
        }

        boolean hasLetter = trimmed.codePoints().anyMatch(Character::isLetter);
        if (!hasLetter) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_REJECTION_REASON|Lý do từ chối phải chứa ít nhất một chữ cái.");
        }

        return trimmed;
    }

    private String buildApproveDetail(UserStatus previousStatus, String decisionNote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previous_status", previousStatus.name());
        payload.put("new_status", UserStatus.ACTIVE.name());
        payload.put("decision_note", decisionNote);
        return serializeDetail(payload);
    }

    private String buildRejectDetail(UserStatus previousStatus, String reason, String decisionNote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previous_status", previousStatus.name());
        payload.put("new_status", UserStatus.REJECTED.name());
        payload.put("reason", reason);
        payload.put("decision_note", decisionNote);
        return serializeDetail(payload);
    }

    private String serializeDetail(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Không thể serialize audit detail, dùng toString thay thế.", ex);
            return payload.toString();
        }
    }

    private void notifyAndEmail(User driver, String type, String title, String message) {
        UUID driverId = driver.getId();

        try {
            notificationService.create(driverId, type, title, message);
        } catch (Exception ex) {
            log.error("Khong the tao thong bao cho tai xe {}.", driverId, ex);
            return;
        }

        try {
            String email = driver.getEmail();
            if (email == null || email.isBlank()) {
                return;
            }
            emailService.send(email, title, message);
        } catch (Exception ex) {
            log.error("Khong the gui email cho tai xe {}.", driverId, ex);
        }
    }
}
