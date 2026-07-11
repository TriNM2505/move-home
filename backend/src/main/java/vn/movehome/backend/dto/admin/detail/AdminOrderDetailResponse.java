package vn.movehome.backend.dto.admin.detail;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiet 1 don hang cho Admin (GET /api/admin/orders/{id}).
 * JSON snake_case (dong bo voi cac DTO detail khac cua Admin).
 */
public record AdminOrderDetailResponse(
        @JsonProperty("order") OrderSection order,
        @JsonProperty("customer") PartySection customer,
        @JsonProperty("driver") PartySection driver,
        @JsonProperty("pickup") LocationSection pickup,
        @JsonProperty("dropoff") LocationSection dropoff,
        @JsonProperty("pricing") PricingSection pricing,
        @JsonProperty("timeline") List<TimelineItem> timeline,
        @JsonProperty("transactions") List<TransactionItem> transactions
) {
    public static record OrderSection(
            @JsonProperty("id") UUID id,
            @JsonProperty("order_code") String orderCode,
            @JsonProperty("status") String status,
            @JsonProperty("vehicle_type") String vehicleType,
            @JsonProperty("porter_count") Integer porterCount,
            @JsonProperty("distance_km") BigDecimal distanceKm,
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("notes") String notes,
            @JsonProperty("cancellation_reason") String cancellationReason
    ) {
    }

    public static record PartySection(
            @JsonProperty("id") UUID id,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("phone_masked") String phoneMasked
    ) {
    }

    public static record LocationSection(
            @JsonProperty("address") String address,
            @JsonProperty("district") String district
    ) {
    }

    public static record PricingSection(
            @JsonProperty("base_fare") BigDecimal baseFare,
            @JsonProperty("peak_surcharge") BigDecimal peakSurcharge,
            @JsonProperty("alley_surcharge") BigDecimal alleySurcharge,
            @JsonProperty("floor_surcharge") BigDecimal floorSurcharge,
            @JsonProperty("porter_fee") BigDecimal porterFee,
            @JsonProperty("total_quote") BigDecimal totalQuote,
            @JsonProperty("commission_rate_snapshot") BigDecimal commissionRateSnapshot
    ) {
    }

    public static record TimelineItem(
            @JsonProperty("label") String label,
            @JsonProperty("at") OffsetDateTime at
    ) {
    }

    public static record TransactionItem(
            @JsonProperty("type") String type,
            @JsonProperty("type_label") String typeLabel,
            @JsonProperty("user_name") String userName,
            @JsonProperty("amount") BigDecimal amount
    ) {
    }
}
