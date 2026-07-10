package vn.movehome.backend.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.DriverDocumentService;

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
    private final DriverProfileRepository driverProfileRepository;
    private final DriverDocumentService driverDocumentService;

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
     * Thong tin doi chieu tai xe/xe cho khach (anh chan dung + anh xe cua tai xe da nhan don).
     * Chi tra khi don da co tai xe. cancellable=true khi don dang ACCEPTED (con huy duoc neu khong khop).
     */
    public CustomerDriverVerificationResponse getDriverVerification(UUID customerId, UUID orderId) {
        ServiceOrder order = orderRepository.findByIdAndCustomerIdAndDeletedAtIsNull(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));

        if (order.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "NO_DRIVER_ASSIGNED|Đơn chưa có tài xế nhận nên chưa có ảnh xác thực.");
        }

        UUID driverId = order.getDriverId();
        User driver = userRepository.findById(driverId).orElse(null);
        DriverProfile profile = driverProfileRepository.findByUserId(driverId).orElse(null);
        // Anh xe cho khach doi chieu: uu tien anh dang truoc (bien so ro); fallback anh xe kieu cu.
        Map<String, String> photos = driverDocumentService.latestSignedUrlsByType(
                driverId, Set.of("FACE_PHOTO", "VEHICLE_PHOTO_FRONT", "VEHICLE_PHOTO"));
        String vehiclePhotoUrl = photos.getOrDefault("VEHICLE_PHOTO_FRONT", photos.get("VEHICLE_PHOTO"));

        return new CustomerDriverVerificationResponse(
                driver != null ? driver.getFullName() : null,
                driver != null ? driver.getPhone() : null,
                profile != null ? profile.getVehicleType() : order.getVehicleType(),
                profile != null ? profile.getVehiclePlate() : null,
                photos.get("FACE_PHOTO"),
                vehiclePhotoUrl,
                "ACCEPTED".equals(order.getStatus()));
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
