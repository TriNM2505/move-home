package vn.movehome.backend.dto.admin.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PeakHoursReportResponse(
        @JsonProperty("period") FinancialReportResponse.Period period,
        @JsonProperty("heatmap") List<HeatmapCell> heatmap,
        @JsonProperty("peak_hour_insight") PeakHourInsight peakHourInsight,
        @JsonProperty("excluded_missing_schedule") long excludedMissingSchedule,
        @JsonProperty("data_quality") List<FinancialReportResponse.DataQualityWarning> dataQuality
) {
    public static record HeatmapCell(
            @JsonProperty("weekday") int weekday,
            @JsonProperty("hour") int hour,
            @JsonProperty("order_count") long orderCount,
            @JsonProperty("completed_count") long completedCount
    ) {
    }

    public static record PeakHourInsight(
            @JsonProperty("top_weekday") Integer topWeekday,
            @JsonProperty("top_hour") Integer topHour,
            @JsonProperty("top_cell_order_count") Long topCellOrderCount
    ) {
    }
}
