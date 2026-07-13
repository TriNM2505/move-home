package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.CustomerRefundService;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manager duyet yeu cau hoan coc do khach huy don (CONFIRMED, chua co tai xe).
 * - refund: hoan coc 30% ve customer_wallet (AC-13 ghi transaction, HR-18 vi khong am).
 * - reject: tu choi kem ly do (khong hoan).
 * RBAC: endpoint /api/manager/** da gioi han MANAGER o SecurityConfig (HR-10).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerCancellationRefundService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.3000");
    private static final Set<String> VALID_STATUSES = Set.of(
            OrderCancellationRefund.STATUS_PENDING,
            OrderCancellationRefund.STATUS_REFUNDED,
            OrderCancellationRefund.STATUS_REJECTED);

    private final OrderCancellationRefundRepository refundRepository;
    private final OrderCancellationPhotoService photoService;
    private final OrderRepository orderRepository;
    private final vn.movehome.backend.repository.UserRepository userRepository;
    private final CustomerRefundService customerRefundService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Page<CancellationRefundListItem> list(String status, int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        String normalized = normalizeStatus(status);

        Page<OrderCancellationRefund> rows;
        if (normalized == null) {
            rows = refundRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else if (OrderCancellationRefund.STATUS_PENDING.equals(normalized)) {
            rows = refundRepository.findByStatusOrderByCreatedAtAsc(normalized, pageable);
        } else {
            rows = refundRepository.findByStatusOrderByCreatedAtDesc(normalized, pageable);
        }

        Map<UUID, ServiceOrder> orders = loadOrders(rows.getContent().stream()
                .map(OrderCancellationRefund::getOrderId).toList());
        Map<UUID, User> users = loadUsers(rows.getContent().stream()
                .map(OrderCancellationRefund::getCustomerId).toList());

        return rows.map(row -> {
            ServiceOrder order = orders.get(row.getOrderId());
            User customer = users.get(row.getCustomerId());
            return new CancellationRefundListItem(
                    row.getId(),
                    row.getOrderId(),
                    order != null ? order.getOrderCode() : null,
                    row.getCustomerId(),
                    customer != null ? customer.getFullName() : null,
                    row.getReason(),
                    row.getStatus(),
                    order != null ? depositOf(order) : null,
                    row.getRefundAmount(),
                    row.getCreatedAt(),
                    row.getProcessedAt());
        });
    }

    @Transactional(readOnly = true)
    public CancellationRefundDetailResponse detail(UUID id) {
        OrderCancellationRefund row = refundRepository.findById(id)
                .orElseThrow(this::notFound);
        ServiceOrder order = orderRepository.findById(row.getOrderId()).orElse(null);
        User customer = userRepository.findById(row.getCustomerId()).orElse(null);
        List<String> photoUrls = photoService.signedUrls(row.getId());

        return new CancellationRefundDetailResponse(
                row.getId(),
                row.getOrderId(),
                order != null ? order.getOrderCode() : null,
                order != null ? order.getStatus() : null,
                row.getCustomerId(),
                customer != null ? customer.getFullName() : null,
                customer != null ? customer.getPhone() : null,
                row.getReason(),
                row.getStatus(),
                order != null ? depositOf(order) : null,
                row.getRefundAmount(),
                row.getRejectionReason(),
                row.getProcessedBy(),
                row.getProcessedAt(),
                row.getCreatedAt(),
                photoUrls,
                order == null ? null : new CancellationRefundDetailResponse.OrderSummary(
                        order.getPickupAddress(),
                        order.getDropoffAddress(),
                        order.getTotalQuote(),
                        order.getScheduledAt()));
    }

    /** Manager bam "Hoan coc" → cong coc 30% vao vi khach + ghi transaction REFUND. */
    @Transactional
    public CancellationRefundDetailResponse refund(UUID id, User actor) {
        OrderCancellationRefund row = findPendingForUpdate(id);
        ServiceOrder order = orderRepository.findByIdForUpdate(row.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        BigDecimal deposit = depositOf(order);
        if (deposit.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NO_DEPOSIT_TO_REFUND|Đơn không có tiền cọc để hoàn.");
        }

        customerRefundService.refundForCancellation(
                row.getCustomerId(), order.getId(), deposit,
                "Hoàn cọc do hủy đơn " + order.getOrderCode());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        row.setStatus(OrderCancellationRefund.STATUS_REFUNDED);
        row.setRefundAmount(deposit);
        row.setProcessedBy(actor.getId());
        row.setProcessedAt(now);
        refundRepository.saveAndFlush(row);

        auditService.log(actor.getId(), actor.getEmail(), "CANCELLATION_REFUNDED",
                "ORDER_CANCELLATION_REFUND", row.getId().toString(),
                "{\"order_code\":\"" + order.getOrderCode() + "\",\"refund_amount\":" + deposit.toPlainString() + "}");

        safeNotify(row.getCustomerId(), "Đã hoàn cọc đơn đã hủy",
                "Đơn " + order.getOrderCode() + ": bạn đã được hoàn " + deposit.toPlainString()
                        + " VND vào ví.");

        log.info("cancellation_refund_processed actor_id={} order_code={} amount={}",
                actor.getId(), order.getOrderCode(), deposit);
        return detail(row.getId());
    }

    /** Manager tu choi hoan coc kem ly do (khong cong tien). */
    @Transactional
    public CancellationRefundDetailResponse reject(UUID id, User actor, String rawReason) {
        String reason = normalizeReason(rawReason);
        OrderCancellationRefund row = findPendingForUpdate(id);
        ServiceOrder order = orderRepository.findById(row.getOrderId()).orElse(null);
        String orderCode = order != null ? order.getOrderCode() : row.getOrderId().toString();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        row.setStatus(OrderCancellationRefund.STATUS_REJECTED);
        row.setRejectionReason(reason);
        row.setProcessedBy(actor.getId());
        row.setProcessedAt(now);
        refundRepository.saveAndFlush(row);

        auditService.log(actor.getId(), actor.getEmail(), "CANCELLATION_REFUND_REJECTED",
                "ORDER_CANCELLATION_REFUND", row.getId().toString(),
                "{\"order_code\":\"" + orderCode + "\"}");

        safeNotify(row.getCustomerId(), "Yêu cầu hoàn cọc bị từ chối",
                "Đơn " + orderCode + ": yêu cầu hoàn cọc không được chấp nhận. Lý do: " + reason);

        return detail(row.getId());
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    // ===================== helpers =====================

    /** Coc = 30% total (commission snapshot), VND nguyen dong lam tron xuong (AC-08). */
    private BigDecimal depositOf(ServiceOrder order) {
        BigDecimal total = order.getTotalQuote() != null ? order.getTotalQuote() : BigDecimal.ZERO;
        BigDecimal rate = order.getCommissionRateSnapshot() != null
                ? order.getCommissionRateSnapshot() : DEFAULT_COMMISSION_RATE;
        return total.multiply(rate).setScale(0, RoundingMode.FLOOR);
    }

    private OrderCancellationRefund findPendingForUpdate(UUID id) {
        OrderCancellationRefund row = refundRepository.findByIdForUpdate(id)
                .orElseThrow(this::notFound);
        if (!OrderCancellationRefund.STATUS_PENDING.equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CANCELLATION_ALREADY_PROCESSED|Yêu cầu hoàn cọc đã được xử lý.");
        }
        return row;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "CANCELLATION_NOT_FOUND|Không tìm thấy yêu cầu hoàn cọc.");
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 3 || normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Lý do từ chối phải từ 3 đến 500 ký tự.");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Trạng thái yêu cầu hoàn cọc không hợp lệ.");
        }
        return normalized;
    }

    private PageRequest createPageRequest(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Số trang không hợp lệ.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.");
        }
        return PageRequest.of(page, size);
    }

    private Map<UUID, ServiceOrder> loadOrders(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findAllById(orderIds).stream()
                .distinct()
                .collect(Collectors.toMap(ServiceOrder::getId, Function.identity()));
    }

    private Map<UUID, User> loadUsers(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .distinct()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private void safeNotify(UUID userId, String title, String message) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.create(userId, NotificationType.ORDER_CANCELLED, title, message);
        } catch (Exception ex) {
            log.warn("Khong the tao notification hoan coc cho user {}: {}", userId, ex.getMessage());
        }
    }
}
