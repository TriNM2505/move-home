package vn.movehome.backend.driver;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverOrderQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    // Don kha dung cho tai xe nhan = don da coc 30% (CONFIRMED), chua co tai xe.
    private static final String AVAILABLE_STATUS = "CONFIRMED";
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "PENDING",
            "PENDING_PAYMENT",
            "CONFIRMED",
            "ASSIGNED",
            "ACCEPTED",
            "IN_PROGRESS",
            "AWAITING_FINAL_PAYMENT",
            "COMPLETED",
            "CANCELLED",
            "DISPUTED",
            "IN_DISPUTE"
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<DriverOrderListItemDTO> getAvailableOrders(int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        return orderRepository.findByStatusAndDeletedAtIsNull(AVAILABLE_STATUS, pageable)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public Page<DriverOrderListItemDTO> getDriverOrders(UUID driverId, String status, int page, int size) {
        PageRequest pageable = createPageRequest(page, size);
        String normalizedStatus = normalizeStatus(status);

        Page<ServiceOrder> orders = normalizedStatus == null
                ? orderRepository.findByDriverIdAndDeletedAtIsNull(driverId, pageable)
                : orderRepository.findByDriverIdAndStatusAndDeletedAtIsNull(driverId, normalizedStatus, pageable);

        return orders.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public DriverOrderDetailDTO getOrderDetail(UUID driverId, UUID orderId) {
        ServiceOrder order = orderRepository.findById(orderId)
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> isAvailable(item) || isAssignedToDriver(item, driverId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        return toDetail(order, driverId);
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
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

        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }

        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Trạng thái đơn hàng không hợp lệ.");
        }
        return normalized;
    }

    private boolean isAvailable(ServiceOrder order) {
        return AVAILABLE_STATUS.equals(order.getStatus()) && order.getDriverId() == null;
    }

    private boolean isAssignedToDriver(ServiceOrder order, UUID driverId) {
        return driverId.equals(order.getDriverId());
    }

    private DriverOrderListItemDTO toListItem(ServiceOrder order) {
        return new DriverOrderListItemDTO(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getVehicleType(),
                order.getPickupDistrict(),
                order.getDropoffDistrict(),
                order.getScheduledAt(),
                order.getDistanceKm(),
                order.getTotalQuote(),
                order.getCreatedAt()
        );
    }

    private DriverOrderDetailDTO toDetail(ServiceOrder order, UUID driverId) {
        User customer = order.getCustomerId() == null
                ? null
                : userRepository.findById(order.getCustomerId()).orElse(null);
        String customerName = customer != null ? customer.getFullName() : null;
        String customerPhone = customer != null ? customer.getPhone() : null;

        return new DriverOrderDetailDTO(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                isAvailable(order),
                isAssignedToDriver(order, driverId),
                order.getFinalPaidAt() != null,
                customerName,
                customerPhone,
                order.getVehicleType(),
                order.getPorterCount(),
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
                order.getEstimatedDurationMinutes(),
                order.getBaseFare(),
                order.getPeakSurcharge(),
                order.getAlleySurcharge(),
                order.getFloorSurcharge(),
                order.getPorterFee(),
                order.getTotalQuote(),
                order.getNotes(),
                order.getDriverId(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCompletedAt(),
                order.getCancelledAt()
        );
    }
}
