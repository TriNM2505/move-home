package vn.movehome.backend.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.OrderStatusTransitionService;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private static final String ORDER_COMPLETED = "COMPLETED";
    private static final String ORDER_DISPUTED = "DISPUTED";
    private static final String ORDER_IN_DISPUTE = "IN_DISPUTE";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> OPEN_STATUSES = Set.of(DisputeStatus.OPEN, DisputeStatus.INVESTIGATING);
    private static final Set<String> VALID_CLAIM_TYPES = Set.of(
            "DAMAGE",
            "MISSING_ITEM",
            "LATE_DELIVERY",
            "INAPPROPRIATE_BEHAVIOR",
            "OTHER");
    private static final Set<String> DISPUTED_ORDER_STATUSES = Set.of(ORDER_DISPUTED, ORDER_IN_DISPUTE);

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusTransitionService orderStatusTransitionService;
    private final UserRepository userRepository;
    private final CustomerRefundService customerRefundService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final DriverEarningService driverEarningService;

    @Transactional
    public DisputeActionResponse create(UUID orderId, User customer, CreateDisputeRequest request) {
        requireUser(customer);
        String claimType = normalizeClaimType(request.claimType());
        String statement = normalizeText(request.customerStatement(), "INVALID_CUSTOMER_STATEMENT", 10, 2000);
        BigDecimal claimAmount = positiveMoney(request.claimAmount());

        ServiceOrder order = orderRepository.findByIdAndCustomerIdForUpdate(orderId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));

        if (!ORDER_COMPLETED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_DISPUTE_STATE|Chi co the khieu nai don COMPLETED.");
        }
        if (order.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ORDER_DRIVER_REQUIRED|Don hang chua co tai xe de khieu nai.");
        }
        if (claimAmount.compareTo(money(order.getTotalQuote())) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CLAIM_AMOUNT_EXCEEDS_ORDER_TOTAL|So tien khieu nai vuot tong gia tri don.");
        }
        if (disputeRepository.existsByOrderIdAndStatusIn(orderId, OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_ALREADY_OPEN|Don hang da co khieu nai dang mo.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        orderStatusTransitionService.transition(
                order,
                ORDER_DISPUTED,
                customer.getId(),
                customer.getRole().name(),
                now);

        Dispute dispute = Dispute.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .driverId(order.getDriverId())
                .claimType(claimType)
                .claimAmount(claimAmount)
                .customerStatement(statement)
                .status(DisputeStatus.OPEN)
                .deadline(now.plusDays(3))
                .build();

        try {
            dispute = disputeRepository.saveAndFlush(dispute);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_ALREADY_OPEN|Don hang da co khieu nai dang mo.", ex);
        }

        auditService.log(
                customer.getId(),
                customer.getEmail(),
                "DISPUTE_OPENED",
                "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of(
                        "order_id", order.getId(),
                        "order_code", order.getOrderCode(),
                        "claim_type", claimType,
                        "claim_amount", claimAmount)));

        notifyDisputeOpened(dispute, order);

        return toActionResponse(dispute, order, "Da tao khieu nai don hang.");
    }

    @Transactional(readOnly = true)
    public Page<DisputeListItemResponse> list(String status, int page, int size) {
        validatePage(page, size);
        String normalizedStatus = normalizeOptionalStatus(status);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<Dispute> disputes = normalizedStatus == null
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByStatus(normalizedStatus, pageable);

        Map<UUID, ServiceOrder> orders = loadOrders(disputes.getContent().stream()
                .map(Dispute::getOrderId)
                .toList());
        Map<UUID, User> users = loadUsers(disputes.getContent().stream()
                .flatMap(dispute -> List.of(dispute.getCustomerId(), dispute.getDriverId()).stream())
                .toList());

        return disputes.map(dispute -> toListItem(
                dispute,
                orders.get(dispute.getOrderId()),
                users.get(dispute.getCustomerId()),
                users.get(dispute.getDriverId())));
    }

    @Transactional(readOnly = true)
    public DisputeDetailResponse detail(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND|Khong tim thay khieu nai."));
        ServiceOrder order = orderRepository.findByIdAndDeletedAtIsNull(dispute.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));
        Map<UUID, User> users = loadUsers(List.of(dispute.getCustomerId(), dispute.getDriverId()));
        return toDetail(dispute, order, users.get(dispute.getCustomerId()), users.get(dispute.getDriverId()));
    }

    @Transactional
    public DisputeActionResponse resolve(UUID disputeId, User actor, ResolveDisputeRequest request) {
        requireUser(actor);
        String note = normalizeText(request.note(), "INVALID_RESOLUTION_NOTE", 10, 1000);
        BigDecimal refundAmount = positiveMoney(request.refundAmount());

        Dispute dispute = findOpenForUpdate(disputeId);
        ServiceOrder order = findDisputedOrderForUpdate(dispute.getOrderId());

        if (refundAmount.compareTo(money(order.getTotalQuote())) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "AMOUNT_EXCEEDS_ORDER_TOTAL|So tien hoan vuot tong gia tri don.");
        }

        customerRefundService.refundForDispute(
                dispute.getCustomerId(),
                order.getId(),
                dispute.getId(),
                refundAmount,
                "Hoan tien khieu nai don " + order.getOrderCode());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dispute.setStatus(DisputeStatus.RESOLVED_REFUND);
        dispute.setResolutionAmount(refundAmount);
        dispute.setResolutionNote(note);
        dispute.setResolvedBy(actor.getId());
        dispute.setResolvedAt(now);
        dispute = disputeRepository.saveAndFlush(dispute);

        // Escrow: khieu nai da dong → giai phong 70% cho tai xe (neu chua release)
        releaseDriverEarningIfNeeded(order);

        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "DISPUTE_RESOLVED",
                "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of(
                        "order_id", order.getId(),
                        "order_code", order.getOrderCode(),
                        "refund_amount", refundAmount,
                        "status", dispute.getStatus())));

        notifyDecision(dispute, order, NotificationType.DISPUTE_RESOLVED,
                "Khieu nai da duoc xu ly",
                "Khieu nai don " + order.getOrderCode() + " da duoc xu ly, hoan tien "
                        + refundAmount.toPlainString() + " VND.");

        return toActionResponse(dispute, order, "Da xu ly khieu nai va hoan tien khach hang.");
    }

    @Transactional
    public DisputeActionResponse reject(UUID disputeId, User actor, RejectDisputeRequest request) {
        requireUser(actor);
        String note = normalizeText(request.note(), "INVALID_RESOLUTION_NOTE", 10, 1000);

        Dispute dispute = findOpenForUpdate(disputeId);
        ServiceOrder order = findDisputedOrderForUpdate(dispute.getOrderId());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dispute.setStatus(DisputeStatus.CLOSED_NO_FAULT);
        dispute.setResolutionNote(note);
        dispute.setResolvedBy(actor.getId());
        dispute.setResolvedAt(now);
        dispute = disputeRepository.saveAndFlush(dispute);

        // Escrow: khieu nai bi tu choi (tai xe khong co loi) → giai phong 70% cho tai xe
        releaseDriverEarningIfNeeded(order);

        auditService.log(
                actor.getId(),
                actor.getEmail(),
                "DISPUTE_REJECTED",
                "DISPUTE",
                dispute.getId().toString(),
                serialize(Map.of(
                        "order_id", order.getId(),
                        "order_code", order.getOrderCode(),
                        "status", dispute.getStatus())));

        notifyDecision(dispute, order, NotificationType.DISPUTE_REJECTED,
                "Khieu nai khong duoc chap nhan",
                "Khieu nai don " + order.getOrderCode() + " da dong: " + note);

        return toActionResponse(dispute, order, "Da tu choi khieu nai.");
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    private Dispute findOpenForUpdate(UUID disputeId) {
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND|Khong tim thay khieu nai."));
        if (!OPEN_STATUSES.contains(dispute.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "DISPUTE_ALREADY_RESOLVED|Khieu nai da duoc xu ly.");
        }
        return dispute;
    }

    private ServiceOrder findDisputedOrderForUpdate(UUID orderId) {
        ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));
        if (!DISPUTED_ORDER_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "INVALID_ORDER_DISPUTE_STATE|Trang thai don khong hop le.");
        }
        return order;
    }

    /**
     * Giai phong thu nhap escrow cho tai xe khi khieu nai da dong (resolve/reject).
     * Idempotent: bo qua neu da release (earning_released_at) — tranh cong tien 2 lan.
     * Giu hanh vi cu (tai xe nhan du 70%); hoan tien khach neu co lay tu quy cong ty (refund service).
     */
    private void releaseDriverEarningIfNeeded(ServiceOrder order) {
        if (order.getDriverId() == null || order.getEarningReleasedAt() != null) {
            return;
        }
        driverEarningService.creditEarning(order);
        order.setEarningReleasedAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(order);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|So trang khong hop le.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Kich thuoc trang phai tu 1 den 100.");
        }
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!Set.of(
                DisputeStatus.OPEN,
                DisputeStatus.INVESTIGATING,
                DisputeStatus.RESOLVED_REFUND,
                DisputeStatus.RESOLVED_DEDUCT,
                DisputeStatus.CLOSED_NO_FAULT).contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DISPUTE_FILTER|Bo loc khieu nai khong hop le.");
        }
        return normalized;
    }

    private String normalizeClaimType(String claimType) {
        String normalized = claimType == null ? "" : claimType.trim().toUpperCase();
        if (!VALID_CLAIM_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CLAIM_TYPE|Loai khieu nai khong hop le.");
        }
        return normalized;
    }

    private String normalizeText(String value, String errorCode, int minLength, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        boolean hasLetter = normalized.codePoints().anyMatch(Character::isLetter);
        if (normalized.length() < minLength || normalized.length() > maxLength || !hasLetter) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    errorCode + "|Noi dung khong hop le.");
        }
        return normalized;
    }

    private BigDecimal positiveMoney(BigDecimal value) {
        BigDecimal amount = money(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly khong hop le.");
        }
        return amount;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly khong hop le.");
        }
        try {
            return value.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RESOLUTION_AMOUNT|So tien xu ly phai la VND nguyen dong.");
        }
    }

    private void requireUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui long dang nhap de tiep tuc.");
        }
    }

    private Map<UUID, ServiceOrder> loadOrders(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findAllById(orderIds)
                .stream()
                .distinct()
                .collect(Collectors.toMap(ServiceOrder::getId, Function.identity()));
    }

    private Map<UUID, User> loadUsers(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds)
                .stream()
                .distinct()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private DisputeListItemResponse toListItem(
            Dispute dispute,
            ServiceOrder order,
            User customer,
            User driver
    ) {
        return new DisputeListItemResponse(
                dispute.getId(),
                dispute.getOrderId(),
                order != null ? order.getOrderCode() : null,
                order != null ? order.getStatus() : null,
                dispute.getCustomerId(),
                customer != null ? customer.getFullName() : null,
                dispute.getDriverId(),
                driver != null ? driver.getFullName() : null,
                dispute.getClaimType(),
                dispute.getClaimAmount(),
                dispute.getStatus(),
                dispute.getCreatedAt(),
                dispute.getDeadline());
    }

    private DisputeDetailResponse toDetail(
            Dispute dispute,
            ServiceOrder order,
            User customer,
            User driver
    ) {
        return new DisputeDetailResponse(
                dispute.getId(),
                dispute.getStatus(),
                dispute.getClaimType(),
                dispute.getClaimAmount(),
                dispute.getCustomerStatement(),
                dispute.getDriverResponse(),
                dispute.getDriverResponseAt(),
                dispute.getResolutionAmount(),
                dispute.getResolutionNote(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getCreatedAt(),
                dispute.getDeadline(),
                new DisputeDetailResponse.OrderSummary(
                        order.getId(),
                        order.getOrderCode(),
                        order.getStatus(),
                        order.getTotalQuote(),
                        order.getScheduledAt(),
                        order.getCompletedAt(),
                        order.getPickupAddress(),
                        order.getDropoffAddress(),
                        order.getVehicleType()),
                new DisputeDetailResponse.PartySummary(
                        dispute.getCustomerId(),
                        customer != null ? customer.getFullName() : null,
                        customer != null ? customer.getPhone() : null),
                new DisputeDetailResponse.PartySummary(
                        dispute.getDriverId(),
                        driver != null ? driver.getFullName() : null,
                        driver != null ? driver.getPhone() : null));
    }

    private DisputeActionResponse toActionResponse(Dispute dispute, ServiceOrder order, String message) {
        return new DisputeActionResponse(
                dispute.getId(),
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                dispute.getStatus(),
                dispute.getResolutionAmount(),
                dispute.getResolutionNote(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                message);
    }

    private void notifyDisputeOpened(Dispute dispute, ServiceOrder order) {
        String title = "Khieu nai moi";
        String message = "Don " + order.getOrderCode() + " vua co khieu nai moi.";
        safeNotify(dispute.getCustomerId(), NotificationType.DISPUTE_OPENED, title, message);
        safeNotify(dispute.getDriverId(), NotificationType.DISPUTE_OPENED, title, message);
        notifyOperations(NotificationType.DISPUTE_OPENED, title, message);
    }

    private void notifyDecision(Dispute dispute, ServiceOrder order, String type, String title, String message) {
        safeNotify(dispute.getCustomerId(), type, title, message);
        safeNotify(dispute.getDriverId(), type, title, message);
    }

    private void notifyOperations(String type, String title, String message) {
        userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE)
                .forEach(user -> safeNotify(user.getId(), type, title, message));
        userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE)
                .forEach(user -> safeNotify(user.getId(), type, title, message));
    }

    private void safeNotify(UUID userId, String type, String title, String message) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.create(userId, type, title, message);
        } catch (Exception ex) {
            log.warn("Khong the tao notification {} cho user {}: {}", type, userId, ex.getMessage());
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException ex) {
            return payload.toString();
        }
    }
}
