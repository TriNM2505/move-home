package vn.movehome.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.dto.RouteEstimateResponse;
import vn.movehome.backend.dto.customer.PricingBreakdown;
import vn.movehome.backend.dto.customer.QuoteRequest;
import vn.movehome.backend.dto.customer.QuoteResponse;
import vn.movehome.backend.service.PricingService;

/**
 * Endpoint PUBLIC cho Guest thử tính giá (CONTEXT §Guest Mode, HR-17).
 * KHONG can JWT (SecurityConfig permitAll /api/public/**), KHONG ghi DB.
 * Tai dung nguyen PricingService + OsrmClient nhu ban customer — chi khac la
 * khong gan role, khong luu Order. Chi tra ve du lieu gia (khong PII).
 */
@RestController
@RequiredArgsConstructor
public class PublicQuoteController {

    private final OsrmClient osrmClient;
    private final PricingService pricingService;

    @PostMapping("/api/public/quote-estimate")
    public QuoteResponse estimate(@Valid @RequestBody QuoteRequest request) {
        RouteEstimateResponse route = osrmClient.calculateRoute(
                request.pickup().lat().doubleValue(),
                request.pickup().lng().doubleValue(),
                request.dropoff().lat().doubleValue(),
                request.dropoff().lng().doubleValue());

        PricingBreakdown pricing = pricingService.calculate(
                request.vehicleType(),
                route.distanceKm(),
                request.scheduledAt(),
                request.pickup().hasAlley(),
                request.dropoff().hasAlley(),
                request.pickup().floor(),
                request.pickup().hasElevator(),
                request.dropoff().floor(),
                request.dropoff().hasElevator(),
                request.porterCount());

        return new QuoteResponse(
                route.distanceKm(),
                route.durationMinutes(),
                pricing.baseFare(),
                pricing.peakSurcharge(),
                pricing.alleySurcharge(),
                pricing.floorSurcharge(),
                pricing.porterFee(),
                pricing.totalQuote());
    }
}
