package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerOrderQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "PENDING",
            "PENDING_PAYMENT",
            "CONFIRMED",
            "ASSIGNED",
            "ACCEPTED",
            "IN_PROGRESS",
            "AWAITING_FINAL_PAYMENT",
            "COMPLETED",
            "DISPUTED",
            "IN_DISPUTE",
            "CANCELLED"
    );

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<OrderListItemDTO> getOrders(UUID customerId, String status, int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        String normalizedStatus = normalizeStatus(status);

        Page<ServiceOrder> orders = normalizedStatus == null
                ? orderRepository.findByCustomerIdAndDeletedAtIsNull(customerId, pageable)
                : orderRepository.findByCustomerIdAndStatusAndDeletedAtIsNull(
                        customerId, normalizedStatus, pageable);

        return orders.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public OrderDetailDTO getOrderDetail(UUID customerId, UUID orderId) {
        ServiceOrder order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        if (!customerId.equals(order.getCustomerId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "ORDER_OWNERSHIP_REQUIRED|Bạn chỉ có thể xem đơn hàng của chính mình.");
        }

        return toDetail(order);
    }

    private PageRequest createPageRequest(int page, int size) {
        if (page < 0) {
            throw validationError("Số trang không hợp lệ.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw validationError("Kích thước trang phải từ 1 đến 100.");
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(page, size, sort);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw validationError("Trạng thái lọc không được hỗ trợ.");
        }
        return normalized;
    }

    private ResponseStatusException validationError(String message) {
        return new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR|" + message);
    }

    private OrderListItemDTO toListItem(ServiceOrder order) {
        return OrderListItemDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .totalQuote(order.getTotalQuote())
                .pickupAddress(order.getPickupAddress())
                .pickupDistrict(order.getPickupDistrict())
                .dropoffAddress(order.getDropoffAddress())
                .dropoffDistrict(order.getDropoffDistrict())
                .vehicleType(order.getVehicleType())
                .scheduledAt(order.getScheduledAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailDTO toDetail(ServiceOrder order) {
        return OrderDetailDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .vehicleType(order.getVehicleType())
                .pickupAddress(order.getPickupAddress())
                .pickupDistrict(order.getPickupDistrict())
                .dropoffAddress(order.getDropoffAddress())
                .dropoffDistrict(order.getDropoffDistrict())
                .scheduledAt(order.getScheduledAt())
                .distanceKm(order.getDistanceKm())
                .baseFare(order.getBaseFare())
                .peakSurcharge(order.getPeakSurcharge())
                .alleySurcharge(order.getAlleySurcharge())
                .floorSurcharge(order.getFloorSurcharge())
                .porterFee(order.getPorterFee())
                .totalQuote(order.getTotalQuote())
                .porterCount(order.getPorterCount())
                .notes(order.getNotes())
                .driverId(order.getDriverId())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .build();
    }
}
