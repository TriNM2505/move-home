package vn.movehome.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import vn.movehome.backend.dto.RouteEstimateResponse;

class OsrmClientTest {

    @Test
    void calculateRouteReturnsPositiveDistanceForHanoiCoordinates() {
        OsrmClient osrmClient = new OsrmClient();

        RouteEstimateResponse result = osrmClient.calculateRoute(
                21.028511,
                105.804817,
                21.027764,
                105.834160);

        assertThat(result.distanceKm()).isPositive();
        assertThat(result.durationMinutes()).isPositive();
    }
}
