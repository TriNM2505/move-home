package vn.movehome.backend.dto.admin.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DriversReportResponse(
        @JsonProperty("period") FinancialReportResponse.Period period,
        @JsonProperty("total_drivers_at_period_end") long totalDriversAtPeriodEnd,
        @JsonProperty("active_drivers_at_period_end") long activeDriversAtPeriodEnd,
        @JsonProperty("online_ratio_average") BigDecimal onlineRatioAverage,
        @JsonProperty("top_earners") List<TopEarnerItem> topEarners,
        @JsonProperty("rating_distribution") RatingDistribution ratingDistribution,
        @JsonProperty("average_rating_overall") BigDecimal averageRatingOverall,
        @JsonProperty("operational_churn_proxy_count") Long operationalChurnProxyCount,
        @JsonProperty("data_quality") List<FinancialReportResponse.DataQualityWarning> dataQuality
) {
    public static record TopEarnerItem(
            @JsonProperty("driver_id") UUID driverId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("total_earning") BigDecimal totalEarning,
            @JsonProperty("completed_orders") long completedOrders
    ) {
    }

    public static record RatingDistribution(
            @JsonProperty("star_1") long star1,
            @JsonProperty("star_2") long star2,
            @JsonProperty("star_3") long star3,
            @JsonProperty("star_4") long star4,
            @JsonProperty("star_5") long star5,
            @JsonProperty("total") long total
    ) {
    }
}
