package vn.movehome.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.movehome.backend.dto.RouteEstimateResponse;

class OsrmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // ===== calculateRoute() voi RestClient gia lap (khong goi mang that) =====

    @Test
    void calculateRouteParsesDistanceAndDurationFromOsrmResponse() throws Exception {
        JsonNode response = objectMapper.readTree(
                "{\"routes\":[{\"distance\":12345.6,\"duration\":789.0}]}");
        OsrmClient client = clientWithMockedResponse(response);

        RouteEstimateResponse result = client.calculateRoute(21.0, 105.0, 21.1, 105.1);

        assertThat(result.distanceKm()).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(result.durationMinutes()).isEqualTo(14);
    }

    @Test
    void calculateRouteClampsDurationToAtLeastOneMinuteForZeroDistanceTrip() throws Exception {
        JsonNode response = objectMapper.readTree(
                "{\"routes\":[{\"distance\":0,\"duration\":0}]}");
        OsrmClient client = clientWithMockedResponse(response);

        RouteEstimateResponse result = client.calculateRoute(21.0, 105.0, 21.0, 105.0);

        assertThat(result.distanceKm()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.durationMinutes()).isEqualTo(1);
    }

    @Test
    void calculateRouteFallsBackToHaversineWhenHttpResponseBodyIsNull() throws Exception {
        OsrmClient client = clientWithMockedResponse(null);

        RouteEstimateResponse result = client.calculateRoute(
                21.028511, 105.804817, 21.027764, 105.834160);

        assertThat(result.distanceKm()).isPositive();
        assertThat(result.durationMinutes()).isPositive();
    }

    @Test
    void calculateRouteFallsBackToHaversineWhenRoutesArrayIsEmpty() throws Exception {
        JsonNode response = objectMapper.readTree("{\"routes\":[]}");
        OsrmClient client = clientWithMockedResponse(response);

        RouteEstimateResponse result = client.calculateRoute(
                21.028511, 105.804817, 21.027764, 105.834160);

        assertThat(result.distanceKm()).isPositive();
        assertThat(result.durationMinutes()).isPositive();
    }

    @Test
    void calculateRouteFallsBackToHaversineWhenDistanceFieldIsMissing() throws Exception {
        JsonNode response = objectMapper.readTree("{\"routes\":[{\"duration\":100.0}]}");
        OsrmClient client = clientWithMockedResponse(response);

        RouteEstimateResponse result = client.calculateRoute(
                21.028511, 105.804817, 21.027764, 105.834160);

        assertThat(result.distanceKm()).isPositive();
        assertThat(result.durationMinutes()).isPositive();
    }

    @Test
    void calculateRouteFallsBackToHaversineWhenDurationFieldIsMissing() throws Exception {
        JsonNode response = objectMapper.readTree("{\"routes\":[{\"distance\":100.0}]}");
        OsrmClient client = clientWithMockedResponse(response);

        RouteEstimateResponse result = client.calculateRoute(
                21.028511, 105.804817, 21.027764, 105.834160);

        assertThat(result.distanceKm()).isPositive();
        assertThat(result.durationMinutes()).isPositive();
    }

    @Test
    void calculateRouteFallsBackToHaversineWhenHttpCallThrows() throws Exception {
        OsrmClient client = clientWithMockedException(new RestClientException("timeout"));

        RouteEstimateResponse result = client.calculateRoute(
                21.028511, 105.804817, 21.027764, 105.834160);

        assertThat(result.distanceKm()).isPositive();
        assertThat(result.durationMinutes()).isPositive();
    }

    // ===== fetchRouteGeometry() voi RestClient gia lap =====

    @Test
    void fetchRouteGeometryReturnsLatLngPointsConvertedFromGeoJsonLngLatOrder() throws Exception {
        JsonNode response = objectMapper.readTree(
                "{\"routes\":[{\"geometry\":{\"coordinates\":[[105.0,21.0],[105.1,21.1],[105.2,21.2]]}}]}");
        OsrmClient client = clientWithMockedResponse(response);

        List<double[]> points = client.fetchRouteGeometry(21.0, 105.0, 21.2, 105.2);

        assertThat(points).hasSize(3);
        assertThat(points.get(0)).containsExactly(21.0, 105.0);
        assertThat(points.get(1)).containsExactly(21.1, 105.1);
        assertThat(points.get(2)).containsExactly(21.2, 105.2);
    }

    @Test
    void fetchRouteGeometryFallsBackToStraightLineWhenCoordinatesAreMissing() throws Exception {
        JsonNode response = objectMapper.readTree("{\"routes\":[{\"geometry\":{}}]}");
        OsrmClient client = clientWithMockedResponse(response);

        List<double[]> points = client.fetchRouteGeometry(21.0, 105.0, 21.2, 105.2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsExactly(21.0, 105.0);
        assertThat(points.get(1)).containsExactly(21.2, 105.2);
    }

    @Test
    void fetchRouteGeometryFallsBackToStraightLineWhenCoordinatesHaveFewerThanTwoPoints() throws Exception {
        JsonNode response = objectMapper.readTree(
                "{\"routes\":[{\"geometry\":{\"coordinates\":[[105.0,21.0]]}}]}");
        OsrmClient client = clientWithMockedResponse(response);

        List<double[]> points = client.fetchRouteGeometry(21.0, 105.0, 21.2, 105.2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsExactly(21.0, 105.0);
        assertThat(points.get(1)).containsExactly(21.2, 105.2);
    }

    @Test
    void fetchRouteGeometryFallsBackToStraightLineWhenCoordinatesIsNotAnArray() throws Exception {
        JsonNode response = objectMapper.readTree(
                "{\"routes\":[{\"geometry\":{\"coordinates\":\"invalid\"}}]}");
        OsrmClient client = clientWithMockedResponse(response);

        List<double[]> points = client.fetchRouteGeometry(21.0, 105.0, 21.2, 105.2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsExactly(21.0, 105.0);
        assertThat(points.get(1)).containsExactly(21.2, 105.2);
    }

    @Test
    void fetchRouteGeometryFallsBackToStraightLineWhenHttpResponseBodyIsNull() throws Exception {
        OsrmClient client = clientWithMockedResponse(null);

        List<double[]> points = client.fetchRouteGeometry(21.0, 105.0, 21.2, 105.2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsExactly(21.0, 105.0);
        assertThat(points.get(1)).containsExactly(21.2, 105.2);
    }

    @Test
    void fetchRouteGeometryFallsBackToStraightLineWhenHttpCallThrows() throws Exception {
        OsrmClient client = clientWithMockedException(new RestClientException("timeout"));

        List<double[]> points = client.fetchRouteGeometry(21.0, 105.0, 21.2, 105.2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsExactly(21.0, 105.0);
        assertThat(points.get(1)).containsExactly(21.2, 105.2);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private OsrmClient clientWithMockedResponse(JsonNode jsonResponse) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(jsonResponse);

        OsrmClient client = new OsrmClient();
        ReflectionTestUtils.setField(client, "restClient", restClient);
        return client;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private OsrmClient clientWithMockedException(RuntimeException exceptionToThrow) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenThrow(exceptionToThrow);

        OsrmClient client = new OsrmClient();
        ReflectionTestUtils.setField(client, "restClient", restClient);
        return client;
    }
}
