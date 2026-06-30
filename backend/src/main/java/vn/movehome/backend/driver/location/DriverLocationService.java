package vn.movehome.backend.driver.location;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverLocationService {

    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of(
            "CONFIRMED",
            "ASSIGNED",
            "IN_PROGRESS");
    private static final Set<String> CUSTOMER_TRACKABLE_STATUSES = Set.of(
            "CONFIRMED",
            "ASSIGNED",
            "IN_PROGRESS");
    private static final long STALE_AFTER_SECONDS = 30;

    private final DriverLocationRepository driverLocationRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void updateLocation(UUID driverId, UpdateDriverLocationRequest request) {
        UUID currentOrderId = orderRepository
                .findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        driverId, ACTIVE_ORDER_STATUSES)
                .map(ServiceOrder::getId)
                .orElse(null);

        int affectedRows = driverLocationRepository.upsertLatest(
                driverId,
                currentOrderId,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speedKmh());

        if (affectedRows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED|Chỉ được cập nhật vị trí tối đa một lần mỗi 10 giây.");
        }
    }

    @Transactional(readOnly = true)
    public DriverLocationResponse getOrderLocation(UUID customerId, UUID orderId) {
        ServiceOrder order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        if (!customerId.equals(order.getCustomerId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "ORDER_OWNERSHIP_REQUIRED|Bạn chỉ có thể theo dõi đơn hàng của chính mình.");
        }

        if (!CUSTOMER_TRACKABLE_STATUSES.contains(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATUS_TRANSITION|Đơn hàng hiện không ở trạng thái có thể theo dõi.");
        }

        UUID driverId = order.getDriverId();
        DriverLocation location = driverId == null
                ? null
                : driverLocationRepository.findById(driverId)
                        .filter(value -> orderId.equals(value.getCurrentOrderId()))
                        .orElse(null);

        if (location == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "DRIVER_LOCATION_NOT_FOUND|Tài xế chưa cập nhật vị trí.");
        }

        boolean stale = location.getRecordedAt()
                .isBefore(Instant.now().minusSeconds(STALE_AFTER_SECONDS));
        return new DriverLocationResponse(
                location.getDriverId(),
                location.getLat(),
                location.getLng(),
                location.getHeading(),
                location.getSpeedKmh(),
                location.getRecordedAt(),
                stale);
    }
}
