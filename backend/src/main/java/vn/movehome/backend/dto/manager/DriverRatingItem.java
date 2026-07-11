package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mot dong danh gia tai xe tren man Manager "Đánh giá tài xế".
 * Manager xem duoc ca comment de kiem tra chat luong tai xe (khac Admin chi xem sao).
 * JSON snake_case theo convention cac DTO manager khac (PendingDriverItem...).
 */
public record DriverRatingItem(
        @JsonProperty("rating_id") UUID ratingId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("driver_id") UUID driverId,
        @JsonProperty("driver_name") String driverName,
        @JsonProperty("customer_name") String customerName,
        Integer stars,
        String comment,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {
}
