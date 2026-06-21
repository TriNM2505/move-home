package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.dto.RouteEstimateResponse;
import vn.movehome.backend.dto.customer.PricingBreakdown;
import vn.movehome.backend.service.PricingService;
import vn.movehome.backend.util.OrderCodeGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String PENDING_STATUS = "PENDING_PAYMENT";
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.3000");

    private final OrderRepository orderRepository;
    private final OsrmClient osrmClient;
    private final PricingService pricingService;

    @Transactional
    public CreateOrderResponse createOrder(UUID customerId, CreateOrderRequest request) {
        RouteEstimateResponse route = osrmClient.calculateRoute(
                request.pickup().lat().doubleValue(),
                request.pickup().lng().doubleValue(),
                request.dropoff().lat().doubleValue(),
                request.dropoff().lng().doubleValue());

        PricingBreakdown pricing = pricingService.calculate(
                request.vehicleType(),
                route.distanceKm(),
                request.scheduledAt().toInstant(),
                request.pickup().hasAlley(),
                request.dropoff().hasAlley(),
                request.pickup().floor(),
                request.pickup().hasElevator(),
                request.dropoff().floor(),
                request.dropoff().hasElevator(),
                request.porterCount());

        ServiceOrder order = ServiceOrder.builder()
                .orderCode(generateUniqueOrderCode())
                .customerId(customerId)
                .pickupAddress(request.pickup().address().trim())
                .pickupDistrict(request.pickup().district())
                .dropoffAddress(request.dropoff().address().trim())
                .dropoffDistrict(request.dropoff().district())
                .scheduledAt(request.scheduledAt())
                .status(PENDING_STATUS)
                .totalQuote(money(pricing.totalQuote()))
                .commissionRateSnapshot(COMMISSION_RATE)
                .distanceKm(route.distanceKm())
                .estimatedDurationMinutes(route.durationMinutes())
                .notes(normalizeNotes(request.notes()))
                .vehicleType(request.vehicleType())
                .porterCount(request.porterCount())
                .pickupLat(request.pickup().lat())
                .pickupLng(request.pickup().lng())
                .dropoffLat(request.dropoff().lat())
                .dropoffLng(request.dropoff().lng())
                .pickupFloor(request.pickup().floor())
                .pickupHasElevator(request.pickup().hasElevator())
                .pickupHasAlley(request.pickup().hasAlley())
                .dropoffFloor(request.dropoff().floor())
                .dropoffHasElevator(request.dropoff().hasElevator())
                .dropoffHasAlley(request.dropoff().hasAlley())
                .baseFare(money(pricing.baseFare()))
                .peakSurcharge(money(pricing.peakSurcharge()))
                .alleySurcharge(money(pricing.alleySurcharge()))
                .floorSurcharge(money(pricing.floorSurcharge()))
                .porterFee(money(pricing.porterFee()))
                .build();

        ServiceOrder savedOrder = orderRepository.save(order);
        return new CreateOrderResponse(
                savedOrder.getId(),
                savedOrder.getOrderCode(),
                savedOrder.getStatus(),
                savedOrder.getTotalQuote());
    }

    private String generateUniqueOrderCode() {
        String orderCode;
        do {
            orderCode = OrderCodeGenerator.generate();
        } while (orderRepository.existsByOrderCode(orderCode));
        return orderCode;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }
}
