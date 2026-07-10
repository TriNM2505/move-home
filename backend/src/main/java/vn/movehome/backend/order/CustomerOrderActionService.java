package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.DisputeService;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.repository.DriverProfileRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderActionService {

    private static final Set<String> CUSTOMER_CANCELLABLE_STATUSES = Set.of("PENDING", "PENDING_PAYMENT", "CONFIRMED");
    // Bao cao tai xe/xe khong khop chi khi tai xe vua nhan don (ACCEPTED), truoc khi van chuyen
    private static final String MISMATCH_REPORTABLE_STATUS = "ACCEPTED";
    private static final String MISMATCH_REASON =
            "Tài xế hoặc phương tiện không khớp ảnh xác thực (khách báo cáo).";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String CANCELLED_STATUS = "CANCELLED";
    private static final String IN_PROGRESS_STATUS = "IN_PROGRESS";

    private final OrderRepository orderRepository;
    private final OrderStatusTransitionService orderStatusTransitionService;
    private final OrderRatingRepository orderRatingRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DisputeService disputeService;

    @Transactional
    public CancelOrderResponse cancelOrder(
            UUID customerId,
            String changedByRole,
            UUID orderId,
            CancelOrderRequest request
    ) {
        String reason = normalizeReason(request != null ? request.reason() : null);
        ServiceOrder order = findOwnedOrderForUpdate(customerId, orderId);

        if (!CUSTOMER_CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể hủy đơn ở trạng thái đang chờ xử lý.");
        }

        OffsetDateTime cancelledAt = OffsetDateTime.now(ZoneOffset.UTC);
        String previousStatus = order.getStatus();
        order.setCancelledAt(cancelledAt);
        order.setCancellationReason(reason);

        ServiceOrder savedOrder = orderStatusTransitionService.transition(
                order, CANCELLED_STATUS, customerId, changedByRole, cancelledAt);
        log.info("order_state_audit actor_id={} actor_role=CUSTOMER timestamp={} from_state={} to_state={} entity_id={}",
                customerId, cancelledAt, previousStatus, CANCELLED_STATUS, savedOrder.getId());
        return new CancelOrderResponse(
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getCancelledAt(),
                "Đơn hàng đã được hủy.");
    }

    /**
     * Khach bao cao tai xe/xe khong khop anh xac thuc → huy chuyen ngay (chi khi don dang ACCEPTED).
     * Giai phong tai xe (don sang CANCELLED). Hoan tien coc xu ly thu cong theo chinh sach (CONTEXT).
     */
    @Transactional
    public CancelOrderResponse reportDriverMismatch(UUID customerId, String changedByRole, UUID orderId) {
        ServiceOrder order = findOwnedOrderForUpdate(customerId, orderId);

        if (!MISMATCH_REPORTABLE_STATUS.equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Chỉ có thể báo cáo không khớp khi tài xế vừa nhận đơn và chưa bắt đầu vận chuyển.");
        }
        // Chi bao khong khop SAU khi tai xe da den diem don (khach doi chieu nguoi/xe thuc te)
        if (order.getArrivedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NOT_ARRIVED|Chỉ báo cáo không khớp sau khi tài xế đã đến điểm đón.");
        }

        OffsetDateTime cancelledAt = OffsetDateTime.now(ZoneOffset.UTC);
        String previousStatus = order.getStatus();
        order.setCancelledAt(cancelledAt);
        order.setCancellationReason(MISMATCH_REASON);

        ServiceOrder savedOrder = orderStatusTransitionService.transition(
                order, CANCELLED_STATUS, customerId, changedByRole, cancelledAt);
        log.info("order_state_audit actor_id={} actor_role=CUSTOMER timestamp={} from_state={} to_state={} entity_id={} reason=DRIVER_MISMATCH",
                customerId, cancelledAt, previousStatus, CANCELLED_STATUS, savedOrder.getId());

        // Khong tu dong tru tien: dua sang khieu nai cho MANAGER quyet (hoan coc + phat 500k tai xe)
        disputeService.openMismatchDispute(savedOrder, customerId);

        return new CancelOrderResponse(
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getCancelledAt(),
                "Đã hủy chuyến và gửi khiếu nại cho quản lý xem xét hoàn cọc.");
    }

    /**
     * Khach xac nhan tai xe/xe DUNG voi anh (sau khi tai xe da den diem don) → bat dau chuyen.
     * ACCEPTED (arrived_at != null) → IN_PROGRESS, set started_at.
     */
    @Transactional
    public CancelOrderResponse confirmDriverMatch(UUID customerId, String changedByRole, UUID orderId) {
        ServiceOrder order = findOwnedOrderForUpdate(customerId, orderId);

        if (!MISMATCH_REPORTABLE_STATUS.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_STATE|Chỉ xác nhận khi tài xế đang ở trạng thái đã nhận đơn.");
        }
        if (order.getArrivedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NOT_ARRIVED|Tài xế chưa đến điểm đón để đối chiếu.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        order.setStartedAt(now);
        ServiceOrder savedOrder = orderStatusTransitionService.transition(
                order, IN_PROGRESS_STATUS, customerId, changedByRole, now);
        log.info("order_state_audit actor_id={} actor_role=CUSTOMER timestamp={} from_state=ACCEPTED to_state=IN_PROGRESS entity_id={} reason=DRIVER_MATCH_CONFIRMED",
                customerId, now, savedOrder.getId());

        return new CancelOrderResponse(
                savedOrder.getId(),
                savedOrder.getStatus(),
                null,
                "Đã xác nhận đúng tài xế/xe. Chuyến bắt đầu.");
    }

    @Transactional
    public RatingResponse rateOrder(UUID customerId, UUID orderId, RatingRequest request) {
        int stars = validateStars(request != null ? request.stars() : null);
        String comment = normalizeComment(request != null ? request.comment() : null);
        ServiceOrder order = findOwnedOrderForUpdate(customerId, orderId);

        if (!COMPLETED_STATUS.equals(order.getStatus())) {
            throw new IllegalStateException("Chỉ có thể đánh giá đơn hàng đã hoàn thành.");
        }
        if (orderRatingRepository.existsByOrderId(order.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ORDER_ALREADY_RATED|Đơn hàng này đã được đánh giá.");
        }

        OrderRating rating = OrderRating.builder()
                .orderId(order.getId())
                .customerId(customerId)
                .driverId(order.getDriverId())
                .stars(stars)
                .comment(comment)
                .build();

        OrderRating savedRating = orderRatingRepository.saveAndFlush(rating);
        updateDriverAverageRating(order.getDriverId());

        return new RatingResponse(
                savedRating.getId(),
                savedRating.getOrderId(),
                savedRating.getStars(),
                "Cảm ơn bạn đã đánh giá đơn hàng.");
    }

    private ServiceOrder findOwnedOrderForUpdate(UUID customerId, UUID orderId) {
        return orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Vui lòng nhập lý do hủy đơn.");
        }

        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Lý do hủy đơn không được vượt quá 500 ký tự.");
        }
        return normalized;
    }

    private int validateStars(Integer stars) {
        if (stars == null || stars < 1 || stars > 5) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số sao đánh giá phải từ 1 đến 5.");
        }
        return stars;
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }

        String normalized = comment.trim();
        if (normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Nhận xét không được vượt quá 500 ký tự.");
        }
        return normalized;
    }

    private void updateDriverAverageRating(UUID driverId) {
        if (driverId == null) {
            return;
        }

        driverProfileRepository.findByUserId(driverId).ifPresent(profile -> {
            BigDecimal average = orderRatingRepository.calculateAverageStarsByDriverId(driverId)
                    .setScale(2, RoundingMode.HALF_UP);
            profile.setAverageRating(average);
            driverProfileRepository.save(profile);
        });
    }
}
