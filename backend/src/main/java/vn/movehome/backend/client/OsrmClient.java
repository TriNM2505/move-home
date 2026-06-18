package vn.movehome.backend.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import vn.movehome.backend.dto.RouteEstimateResponse;

@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private static final String OSRM_BASE_URL = "https://router.project-osrm.org";
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double ROAD_DISTANCE_FACTOR = 1.3;
    private static final double FALLBACK_AVERAGE_SPEED_KMH = 30.0;

    private final RestClient restClient;

    public OsrmClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(OSRM_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public RouteEstimateResponse calculateRoute(double lat1, double lng1, double lat2, double lng2) {
        try {
            JsonNode response = restClient.get()
                    .uri("/route/v1/driving/{lng1},{lat1};{lng2},{lat2}?overview=false",
                            lng1, lat1, lng2, lat2)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode firstRoute = response == null ? null : response.path("routes").path(0);
            if (firstRoute == null || firstRoute.isMissingNode() || !firstRoute.has("distance") || !firstRoute.has("duration")) {
                throw new IllegalStateException("OSRM không trả về tuyến đường");
            }

            return new RouteEstimateResponse(
                    metersToKilometers(firstRoute.path("distance").asDouble()),
                    secondsToMinutes(firstRoute.path("duration").asDouble()));
        } catch (Exception ex) {
            log.warn("Không gọi được OSRM, dùng ước lượng Haversine cho khoảng cách giữa 2 toạ độ.", ex);
            return fallbackEstimate(lat1, lng1, lat2, lng2);
        }
    }

    private RouteEstimateResponse fallbackEstimate(double lat1, double lng1, double lat2, double lng2) {
        double distanceKm = haversineKm(lat1, lng1, lat2, lng2) * ROAD_DISTANCE_FACTOR;
        int durationMinutes = Math.max(1, (int) Math.ceil(distanceKm / FALLBACK_AVERAGE_SPEED_KMH * 60));

        return new RouteEstimateResponse(toDistanceKm(distanceKm), durationMinutes);
    }

    private BigDecimal metersToKilometers(double meters) {
        return toDistanceKm(meters / 1000.0);
    }

    private BigDecimal toDistanceKm(double kilometers) {
        return BigDecimal.valueOf(kilometers).setScale(2, RoundingMode.HALF_UP);
    }

    private int secondsToMinutes(double seconds) {
        return Math.max(1, (int) Math.ceil(seconds / 60.0));
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double startLat = Math.toRadians(lat1);
        double endLat = Math.toRadians(lat2);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(startLat) * Math.cos(endLat)
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
