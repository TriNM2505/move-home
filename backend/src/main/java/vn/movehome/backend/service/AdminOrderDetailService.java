package vn.movehome.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.detail.AdminOrderDetailResponse;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chi tiet 1 don hang cho Admin. Chi doc (read-only), tong hop tu service_order +
 * ten khach/tai xe + cac giao dich lien quan don.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderDetailService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse orderDetail(UUID orderId) {
        ServiceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Khong tim thay don hang."));

        User customer = order.getCustomerId() != null
                ? userRepository.findById(order.getCustomerId()).orElse(null)
                : null;
        User driver = order.getDriverId() != null
                ? userRepository.findById(order.getDriverId()).orElse(null)
                : null;

        List<Transaction> txs = transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId);
        Map<UUID, User> txUsers = loadUsers(txs.stream()
                .map(Transaction::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return new AdminOrderDetailResponse(
                new AdminOrderDetailResponse.OrderSection(
                        order.getId(),
                        order.getOrderCode(),
                        order.getStatus(),
                        order.getVehicleType(),
                        order.getPorterCount(),
                        order.getDistanceKm(),
                        order.getScheduledAt(),
                        order.getCreatedAt(),
                        order.getNotes(),
                        order.getCancellationReason()
                ),
                party(customer),
                party(driver),
                new AdminOrderDetailResponse.LocationSection(order.getPickupAddress(), order.getPickupDistrict()),
                new AdminOrderDetailResponse.LocationSection(order.getDropoffAddress(), order.getDropoffDistrict()),
                new AdminOrderDetailResponse.PricingSection(
                        order.getBaseFare(),
                        order.getPeakSurcharge(),
                        order.getAlleySurcharge(),
                        order.getFloorSurcharge(),
                        order.getPorterFee(),
                        order.getTotalQuote(),
                        order.getCommissionRateSnapshot()
                ),
                buildTimeline(order),
                txs.stream()
                        .map(tx -> new AdminOrderDetailResponse.TransactionItem(
                                tx.getType() != null ? tx.getType().name() : null,
                                typeLabel(tx.getType()),
                                resolveUserName(txUsers.get(tx.getUserId())),
                                tx.getAmount()
                        ))
                        .toList()
        );
    }

    private AdminOrderDetailResponse.PartySection party(User user) {
        if (user == null) {
            return null;
        }
        return new AdminOrderDetailResponse.PartySection(
                user.getId(),
                user.getFullName(),
                maskPhone(user.getPhone())
        );
    }

    private List<AdminOrderDetailResponse.TimelineItem> buildTimeline(ServiceOrder o) {
        List<AdminOrderDetailResponse.TimelineItem> items = new ArrayList<>();
        addTimeline(items, "Tạo đơn", o.getCreatedAt());
        addTimeline(items, "Tài xế đến điểm đón", o.getArrivedAt());
        addTimeline(items, "Bắt đầu vận chuyển", o.getStartedAt());
        addTimeline(items, "Khách thanh toán phần còn lại", o.getFinalPaidAt());
        addTimeline(items, "Hoàn thành", o.getCompletedAt());
        addTimeline(items, "Giải ngân cho tài xế", o.getEarningReleasedAt());
        addTimeline(items, "Đã hủy đơn", o.getCancelledAt());
        items.sort(Comparator.comparing(
                AdminOrderDetailResponse.TimelineItem::at,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private void addTimeline(List<AdminOrderDetailResponse.TimelineItem> items, String label, OffsetDateTime at) {
        if (at != null) {
            items.add(new AdminOrderDetailResponse.TimelineItem(label, at));
        }
    }

    private Map<UUID, User> loadUsers(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private String resolveUserName(User user) {
        if (user == null) {
            return "Hệ thống";
        }
        return user.getFullName() != null ? user.getFullName() : "Hệ thống";
    }

    private String typeLabel(TransactionType type) {
        if (type == null) {
            return "Giao dịch";
        }
        return switch (type) {
            case DEPOSIT_TOP_UP -> "Đặt cọc tài xế";
            case DEPOSIT_REFUND -> "Hoàn cọc tài xế";
            case ORDER_PAYMENT -> "Thanh toán đơn";
            case WALLET_TOP_UP -> "Nạp ví khách hàng";
            case DRIVER_EARNING -> "Thu nhập tài xế";
            case PLATFORM_FEE -> "Phí nền tảng";
            case DAMAGE_DEDUCTION -> "Khấu trừ khiếu nại";
            case WITHDRAWAL -> "Rút tiền";
            case REFUND -> "Hoàn tiền";
        };
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 3) {
            return "***";
        }
        return "****" + trimmed.substring(trimmed.length() - 3);
    }
}
