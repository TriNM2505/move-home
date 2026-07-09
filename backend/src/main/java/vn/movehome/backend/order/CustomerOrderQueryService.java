package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.UserRepository;

/**
 * Truy van danh sach + chi tiet don cua chinh Customer dang dang nhap.
 * Read-only. Moi truy van deu filter theo customerId (HR-10 — khach chi thay don cua minh).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomerOrderQueryService {

    // Trang thai ket thuc — dung de tach "dang cho" vs "lich su"
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "CANCELLED");
    // Trang thai chua tra coc (deposit chua thanh toan)
    private static final Set<String> DEPOSIT_UNPAID_STATUSES = Set.of("PENDING", "PENDING_PAYMENT");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OsrmClient osrmClient;

    /**
     * Lay don cua Customer theo scope.
     * scope = "history" → don da COMPLETED/CANCELLED.
     * scope khac (mac dinh "pending") → don chua ket thuc.
     */
    public Page<CustomerOrderItem> getMyOrders(UUID customerId, String scope, Pageable pageable) {
        if ("history".equalsIgnoreCase(scope)) {
            return orderRepository.findCustomerOrdersByStatusIn(customerId, TERMINAL_STATUSES, pageable);
        }
        return orderRepository.findCustomerOrdersByStatusNotIn(customerId, TERMINAL_STATUSES, pageable);
    }

    /**
     * Chi tiet 1 don cua chinh Customer (HR-10), kem thong tin coc 30% da tra / con lai 70%.
     */
    public CustomerOrderDetailResponse getOrderDetail(UUID customerId, UUID orderId) {
        ServiceOrder order = orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        BigDecimal deposit = OrderDepositCalculator.deposit(order);
        BigDecimal remaining = order.getTotalQuote().subtract(deposit);
        boolean depositPaid = !DEPOSIT_UNPAID_STATUSES.contains(order.getStatus());
        boolean finalPaid = order.getFinalPaidAt() != null;

        String driverName = null;
        if (order.getDriverId() != null) {
            driverName = userRepository.findById(order.getDriverId())
                    .map(User::getFullName)
                    .orElse(null);
        }
        // (route lay rieng qua getOrderRoute de ve tuyen tren map khach)

        return new CustomerOrderDetailResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getVehicleType(),
                order.getPickupAddress(),
                order.getPickupDistrict(),
                order.getPickupLat(),
                order.getPickupLng(),
                order.getDropoffAddress(),
                order.getDropoffDistrict(),
                order.getDropoffLat(),
                order.getDropoffLng(),
                order.getScheduledAt(),
                order.getDistanceKm(),
                order.getBaseFare(),
                order.getPeakSurcharge(),
                order.getAlleySurcharge(),
                order.getFloorSurcharge(),
                order.getPorterFee(),
                order.getPorterCount(),
                order.getTotalQuote(),
                deposit,
                depositPaid,
                remaining,
                finalPaid,
                driverName,
                order.getNotes(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getCancelledAt());
    }

    /**
     * Hình tuyến đường thật (danh sách [lat, lng]) điểm đón → điểm trả, để vẽ trên map khách (HR-10).
     */
    public java.util.List<double[]> getOrderRoute(UUID customerId, UUID orderId) {
        ServiceOrder order = orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        if (order.getPickupLat() == null || order.getPickupLng() == null
                || order.getDropoffLat() == null || order.getDropoffLng() == null) {
            return java.util.List.of();
        }
        return osrmClient.fetchRouteGeometry(
                order.getPickupLat().doubleValue(), order.getPickupLng().doubleValue(),
                order.getDropoffLat().doubleValue(), order.getDropoffLng().doubleValue());
    }
}
