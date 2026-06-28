package vn.movehome.backend.dto.admin.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OperationsReportResponse(
        @JsonProperty("period") FinancialReportResponse.Period period,
        @JsonProperty("orders") OrderMetrics orders,
        @JsonProperty("average_order_value") BigDecimal averageOrderValue,
        @JsonProperty("average_distance_km") BigDecimal averageDistanceKm,
        @JsonProperty("peak_hour_orders_rate") BigDecimal peakHourOrdersRate,
        @JsonProperty("average_completion_time_minutes") BigDecimal averageCompletionTimeMinutes,
        @JsonProperty("status_distribution") Map<String, Long> statusDistribution,
        @JsonProperty("completion_trend") List<CompletionTrendPoint> completionTrend,
        @JsonProperty("compare") Compare compare,
        @JsonProperty("data_quality") List<FinancialReportResponse.DataQualityWarning> dataQuality
) {
    public static record OrderMetrics(
            @JsonProperty("total_created") long totalCreated,
            @JsonProperty("completed") long completed,
            @JsonProperty("cancelled") long cancelled,
            @JsonProperty("in_dispute") long inDispute,
            @JsonProperty("terminal_eligible") long terminalEligible,
            @JsonProperty("completion_rate") BigDecimal completionRate,
            @JsonProperty("dispute_rate") BigDecimal disputeRate
    ) {
    }

    public static record CompletionTrendPoint(
            @JsonProperty("bucket") String bucket,
            @JsonProperty("created") long created,
            @JsonProperty("completed") long completed
    ) {
    }

    public static record Compare(
            @JsonProperty("total_created_change_percent") BigDecimal totalCreatedChange,
            @JsonProperty("completion_rate_change_percent") BigDecimal completionRateChange
    ) {
    }
}
