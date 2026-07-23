package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.client.OsrmClient;
import vn.movehome.backend.dto.RouteEstimateResponse;
import vn.movehome.backend.dto.customer.PricingBreakdown;
import vn.movehome.backend.dto.customer.QuoteRequest;
import vn.movehome.backend.dto.customer.QuoteResponse;
import vn.movehome.backend.service.PricingService;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicQuoteControllerTest {

    private final OsrmClient osrmClient = mock(OsrmClient.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final PublicQuoteController controller = new PublicQuoteController(osrmClient, pricingService);

    @Test
    void estimateCombinesRouteEstimateAndPricingBreakdownWithoutRequiringAuth() {
        QuoteRequest.Location pickup = new QuoteRequest.Location(
                "1 Giai Phong", "Hai Ba Trung", new BigDecimal("20.99"), new BigDecimal("105.84"), 0, true, false);
        QuoteRequest.Location dropoff = new QuoteRequest.Location(
                "2 Nguyen Trai", "Thanh Xuan", new BigDecimal("20.98"), new BigDecimal("105.80"), 0, true, false);
        Instant scheduledAt = Instant.parse("2026-06-19T14:00:00Z");
        QuoteRequest request = new QuoteRequest("TRUCK_1T", pickup, dropoff, scheduledAt, 0);

        RouteEstimateResponse route = new RouteEstimateResponse(new BigDecimal("5.50"), 12);
        when(osrmClient.calculateRoute(20.99, 105.84, 20.98, 105.80)).thenReturn(route);

        PricingBreakdown pricing = new PricingBreakdown(
                new BigDecimal("165000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("165000"));
        when(pricingService.calculate(
                "TRUCK_1T", new BigDecimal("5.50"), scheduledAt, false, false, 0, true, 0, true, 0))
                .thenReturn(pricing);

        QuoteResponse response = controller.estimate(request);

        QuoteResponse expected = new QuoteResponse(
                new BigDecimal("5.50"), 12,
                new BigDecimal("165000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("165000"));
        assertThat(response).isEqualTo(expected);
        verify(osrmClient).calculateRoute(20.99, 105.84, 20.98, 105.80);
        verify(pricingService).calculate(
                "TRUCK_1T", new BigDecimal("5.50"), scheduledAt, false, false, 0, true, 0, true, 0);
    }
}
