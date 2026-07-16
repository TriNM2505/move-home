package vn.movehome.backend.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.incident.DriverIncidentService;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverOrderService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String ASSIGNED = "ASSIGNED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String AWAITING_FINAL_PAYMENT = "AWAITING_FINAL_PAYMENT";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    // Trang thai coi la tai xe DANG BAN (chi cho phep 1 don tai 1 thoi diem)
    private static final Set<String> DRIVER_BUSY_STATUSES = Set.of(ACCEPTED, IN_PROGRESS, AWAITING_FINAL_PAYMENT);

    private final OrderRepository orderRepository;
    private final OrderStatusTransitionService orderStatusTransitionService;
    private final DriverEarningService driverEarningService;
    private final NotificationService notificationService;
    private final DriverIncidentService driverIncidentService;

    // Tai xe duoc quyen bam "Khach khong ra" sau N phut ke tu khi den diem don (3 phut cho demo)
    @Value("${app.pickup.no-show-minutes:3}")
    private long noShowMinutes;

    @Transactional
    public void acceptOrder(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);

        if (!CONFIRMED.equals(order.getStatus()) || order.getDriverId() != null) {
            throw new IllegalStateException("Đơn không còn khả dụng");
        }

        // 1 tài xế chỉ làm 1 chuyến tại một thời điểm (CONTEXT) — chặn nhận thêm khi đang bận
        if (orderRepository.existsByDriverIdAndStatusInAndDeletedAtIsNull(driverId, DRIVER_BUSY_STATUSES)) {
            throw new IllegalStateException(
                    "Bạn đang có đơn chưa hoàn thành. Hãy hoàn thành đơn đó trước khi nhận đơn mới.");
        }

        OffsetDateTime changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        order.setDriverId(driverId);
        orderStatusTransitionService.transition(order, ACCEPTED, driverId, changedByRole, changedAt);

        // Neu don nay tung bi ban lai pool do su co van chuyen (V44), dong su co lai (RESOLVED_REASSIGNED).
        driverIncidentService.resolveReassigned(orderId);

        logStateChange(driverId, orderId, CONFIRMED, ACCEPTED, changedAt);
    }

    /**
     * Tai xe bam "Da den diem don": ghi arrived_at, don VAN o ACCEPTED (KHONG chuyen IN_PROGRESS).
     * Chuyen chi thuc su bat dau khi KHACH bam xac nhan doi chieu (confirmDriverMatch → IN_PROGRESS).
     * Idempotent: bam lai khong loi.
     */
    @Transactional
    public void markArrived(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);
        requireOwnership(order, driverId);

        if (!ACCEPTED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_STATE|Chỉ đánh dấu 'đã đến điểm đón' khi đơn đang ở trạng thái đã nhận.");
        }
        if (order.getArrivedAt() != null) {
            return; // da danh dau roi
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        order.setArrivedAt(now);
        orderRepository.save(order);
        logStateChange(driverId, orderId, ACCEPTED, "ACCEPTED(arrived)", now);

        // KHONG co status change nen phai notify khach thu cong de ra doi chieu tai xe/xe
        safeNotifyCustomer(order, NotificationType.DRIVER_ARRIVED,
                "Tài xế đã đến điểm đón",
                "Tài xế đơn " + order.getOrderCode()
                        + " đã đến điểm đón. Vui lòng đối chiếu tài xế/xe với ảnh rồi xác nhận.");
    }

    /**
     * Tai xe bam "Khach khong ra" khi da den diem don qua N phut ma khach khong xac nhan.
     * → huy don; toan bo coc 30% thanh thu nhap tai xe (den bu chuyen hong). Cong ty bo hoa hong.
     */
    @Transactional
    public void cancelNoShow(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);
        requireOwnership(order, driverId);

        if (!ACCEPTED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_STATE|Chỉ hủy do khách vắng khi đơn đang ở trạng thái đã nhận.");
        }
        if (order.getArrivedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NOT_ARRIVED|Bạn cần bấm 'Đã đến điểm đón' trước.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (now.isBefore(order.getArrivedAt().plusMinutes(noShowMinutes))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NO_SHOW_TOO_EARLY|Vui lòng chờ đủ " + noShowMinutes
                            + " phút sau khi đến điểm đón mới được hủy.");
        }

        // Coc 30% thanh thu nhap tai xe (den bu)
        driverEarningService.creditNoShowCompensation(order);

        order.setCancelledAt(now);
        order.setCancellationReason("Khách không có mặt tại điểm đón (no-show).");
        orderStatusTransitionService.transition(order, CANCELLED, driverId, changedByRole, now);
        logStateChange(driverId, orderId, ACCEPTED, CANCELLED, now);

        safeNotifyCustomer(order, NotificationType.ORDER_CANCELLED,
                "Đơn đã hủy do bạn vắng mặt",
                "Đơn " + order.getOrderCode()
                        + " đã bị hủy do bạn không có mặt tại điểm đón. Tiền cọc thuộc về tài xế theo chính sách.");
    }

    private void safeNotifyCustomer(ServiceOrder order, String type, String title, String message) {
        if (order.getCustomerId() == null) {
            return;
        }
        try {
            notificationService.create(order.getCustomerId(), type, title, message);
        } catch (Exception ex) {
            log.warn("Khong the tao notification {} cho khach {}: {}", type, order.getCustomerId(), ex.getMessage());
        }
    }

    /**
     * Tài xế yêu cầu khách thanh toán nốt 70% (IN_PROGRESS → AWAITING_FINAL_PAYMENT).
     * Khách sẽ trả 70% qua Ví/VNPay; khi trả xong tài xế mới bấm Hoàn thành.
     */
    @Transactional
    public void requestFinalPayment(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);
        requireOwnership(order, driverId);

        if (!IN_PROGRESS.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể yêu cầu thanh toán khi đơn đang được thực hiện");
        }

        OffsetDateTime changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        orderStatusTransitionService.transition(
                order, AWAITING_FINAL_PAYMENT, driverId, changedByRole, changedAt);

        logStateChange(driverId, orderId, IN_PROGRESS, AWAITING_FINAL_PAYMENT, changedAt);
    }

    @Transactional
    public void completeOrder(UUID driverId, String changedByRole, UUID orderId) {
        ServiceOrder order = findForUpdate(orderId);
        requireOwnership(order, driverId);

        // HR-05/06: chỉ hoàn thành khi đã ở bước chờ trả nốt và khách đã trả đủ 70%
        if (!AWAITING_FINAL_PAYMENT.equals(order.getStatus())) {
            throw new IllegalStateException("Cần yêu cầu khách thanh toán 70% trước khi hoàn thành");
        }
        if (order.getFinalPaidAt() == null) {
            throw new IllegalStateException("Khách chưa thanh toán nốt 70%, chưa thể hoàn thành đơn");
        }

        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        order.setCompletedAt(completedAt);
        orderStatusTransitionService.transition(
                order, COMPLETED, driverId, changedByRole, completedAt);

        // Escrow 2h (CONTEXT): KHONG cong tien ngay. Sau 2h khong khieu nai, EscrowReleaseService
        // (scheduled) se cong 70% vao vi tai xe va set earning_released_at. Neu khach khieu nai
        // trong thoi gian escrow, tien duoc giai phong khi Manager dong khieu nai (DisputeService).
        logStateChange(driverId, orderId, AWAITING_FINAL_PAYMENT, COMPLETED, completedAt);
    }

    private ServiceOrder findForUpdate(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .filter(order -> order.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));
    }

    private void requireOwnership(ServiceOrder order, UUID driverId) {
        if (!driverId.equals(order.getDriverId())) {
            throw new AccessDeniedException("Bạn chỉ có thể thao tác đơn hàng của chính mình.");
        }
    }

    private void logStateChange(
            UUID driverId,
            UUID orderId,
            String fromState,
            String toState,
            OffsetDateTime timestamp
    ) {
        log.info(
                "order_state_audit actor_id={} actor_role=DRIVER timestamp={} from_state={} to_state={} entity_id={}",
                driverId,
                timestamp,
                fromState,
                toState,
                orderId);
    }
}
