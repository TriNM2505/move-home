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

class CustomerQuoteControllerTest {

    private final OsrmClient osrmClient = mock(OsrmClient.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final CustomerQuoteController controller = new CustomerQuoteController(osrmClient, pricingService);

    @Test
    void quoteCombinesRouteEstimateAndPricingBreakdown() {
        QuoteRequest.Location pickup = new QuoteRequest.Location(
                "123 Lang Ha", "Dong Da", new BigDecimal("21.01"), new BigDecimal("105.81"), 1, false, true);
        QuoteRequest.Location dropoff = new QuoteRequest.Location(
                "456 Cau Giay", "Cau Giay", new BigDecimal("21.03"), new BigDecimal("105.79"), 5, false, false);
        Instant scheduledAt = Instant.parse("2026-06-19T07:00:00Z");
        QuoteRequest request = new QuoteRequest("TRUCK_500KG", pickup, dropoff, scheduledAt, 2);

        RouteEstimateResponse route = new RouteEstimateResponse(new BigDecimal("10.00"), 20);
        when(osrmClient.calculateRoute(21.01, 105.81, 21.03, 105.79)).thenReturn(route);

        PricingBreakdown pricing = new PricingBreakdown(
                new BigDecimal("150000"), new BigDecimal("45000"), new BigDecimal("200000"),
                new BigDecimal("100000"), new BigDecimal("600000"), new BigDecimal("1095000"));
        when(pricingService.calculate(
                "TRUCK_500KG", new BigDecimal("10.00"), scheduledAt, true, false, 1, false, 5, false, 2))
                .thenReturn(pricing);

        QuoteResponse response = controller.quote(request);

        QuoteResponse expected = new QuoteResponse(
                new BigDecimal("10.00"), 20,
                new BigDecimal("150000"), new BigDecimal("45000"), new BigDecimal("200000"),
                new BigDecimal("100000"), new BigDecimal("600000"), new BigDecimal("1095000"));
        assertThat(response).isEqualTo(expected);
        verify(osrmClient).calculateRoute(21.01, 105.81, 21.03, 105.79);
        verify(pricingService).calculate(
                "TRUCK_500KG", new BigDecimal("10.00"), scheduledAt, true, false, 1, false, 5, false, 2);
    }
}
