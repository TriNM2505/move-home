package vn.movehome.backend.incident;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.util.Set;
import java.util.UUID;

/**
 * Tai xe bao su co giua chuyen (hong xe / ly do bat ngo khong the giao).
 * Chi cho phep khi don o ACCEPTED (dang tren duong / da den diem don) hoac IN_PROGRESS (dang giao) —
 * tuc TRUOC khi khach tra 70% (AWAITING_FINAL_PAYMENT tro di khong dung luong nay).
 * Tao bao cao PENDING roi bao Manager duyet (ManagerIncidentService xu ly tiep — Pha B/C).
 * Anh dinh kem upload rieng qua DriverIncidentPhotoService (giong luong huy don V41).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverIncidentService {

    private static final String ACCEPTED = "ACCEPTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    // Trang thai don duoc phep bao su co (truoc khi tra 70%).
    private static final Set<String> REPORTABLE_STATUSES = Set.of(ACCEPTED, IN_PROGRESS);
    // Su co con "dang mo" — chan tao trung tren cung 1 don.
    private static final Set<String> OPEN_STATUSES = Set.of(
            DriverIncidentReport.STATUS_PENDING, DriverIncidentReport.STATUS_CONFIRMED);

    private final DriverIncidentReportRepository reportRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Tai xe bam "Bao su co" cho don dang giao cua minh.
     * @return id bao cao su co vua tao (FE dung de upload anh tiep theo).
     */
    @Transactional
    public UUID reportIncident(UUID driverId, UUID orderId, String reason) {
        ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        if (!driverId.equals(order.getDriverId())) {
            throw new AccessDeniedException("Bạn chỉ có thể báo sự cố cho đơn của chính mình.");
        }

        String currentStatus = order.getStatus();
        if (!REPORTABLE_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_STATE|Chỉ báo sự cố khi đang trên đường đến điểm đón hoặc đang giao hàng.");
        }

        String normalizedReason = validateReason(reason);

        // Chan tao trung khi don da co 1 su co dang mo (DB con enforce bang partial unique index).
        if (reportRepository.existsByOrderIdAndStatusIn(orderId, OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INCIDENT_ALREADY_OPEN|Đơn này đã có báo cáo sự cố đang được xử lý.");
        }

        DriverIncidentReport report = reportRepository.saveAndFlush(DriverIncidentReport.builder()
                .orderId(orderId)
                .driverId(driverId)
                .reason(normalizedReason)
                .orderStatusSnapshot(currentStatus)
                .status(DriverIncidentReport.STATUS_PENDING)
                .build());

        notifyManagers(order.getOrderCode());

        log.info("driver_incident_reported actor_id={} actor_role=DRIVER order_id={} order_code={} order_status={} incident_id={}",
                driverId, orderId, order.getOrderCode(), currentStatus, report.getId());

        return report.getId();
    }

    /**
     * Goi khi 1 tai xe khac nhan lai don (acceptOrder): dong su co CONFIRMED -> RESOLVED_REASSIGNED.
     * Chay trong transaction cua acceptOrder (Propagation.MANDATORY). Idempotent: khong co su co
     * CONFIRMED thi bo qua (da giao thanh cong hoac chua tung co su co).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void resolveReassigned(UUID orderId) {
        reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_CONFIRMED)
                .ifPresent(report -> {
                    report.setStatus(DriverIncidentReport.STATUS_RESOLVED_REASSIGNED);
                    reportRepository.saveAndFlush(report);
                    safeNotify(report.getDriverId(), "Đơn đã có tài xế mới",
                            "Đơn bạn báo sự cố đã được tài xế khác tiếp nhận và tiếp tục.");
                    log.info("incident_resolved_reassigned order_id={} incident_id={}", orderId, report.getId());
                });
    }

    private String validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Vui lòng mô tả sự cố.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Mô tả sự cố không được vượt quá 500 ký tự.");
        }
        return trimmed;
    }

    // Bao moi Manager ACTIVE co su co moi cho xac nhan. Loi notification khong lam fail viec tao bao cao.
    private void notifyManagers(String orderCode) {
        String title = "Tài xế báo sự cố";
        String message = "Đơn " + orderCode + " có tài xế báo sự cố giữa chuyến — cần xác nhận để xử lý.";
        userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE)
                .forEach(manager -> safeNotify(manager.getId(), title, message));
    }

    private void safeNotify(UUID userId, String title, String message) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.create(userId, NotificationType.DRIVER_INCIDENT_REPORTED, title, message);
        } catch (Exception ex) {
            log.warn("Khong the tao notification DRIVER_INCIDENT_REPORTED cho user {}: {}", userId, ex.getMessage());
        }
    }
}
