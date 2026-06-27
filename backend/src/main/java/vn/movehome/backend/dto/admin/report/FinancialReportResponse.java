package vn.movehome.backend.dto.admin.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record FinancialReportResponse(
        @JsonProperty("period") Period period,
        @JsonProperty("gross_booking_value") GrossBookingValue grossBookingValue,
        @JsonProperty("platform_fee") PlatformFee platformFee,
        @JsonProperty("refunds") BigDecimal refunds,
        @JsonProperty("damage_recovery") BigDecimal damageRecovery,
        @JsonProperty("management_net_contribution") BigDecimal managementNetContribution,
        @JsonProperty("compare") Compare compare,
        @JsonProperty("trend") List<TrendPoint> trend,
        @JsonProperty("data_quality") List<DataQualityWarning> dataQuality
) {
    public static record Period(
            @JsonProperty("start") LocalDate start,
            @JsonProperty("end") LocalDate end
    ) {
    }

    public static record GrossBookingValue(
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("breakdown_by_vehicle") Map<String, BigDecimal> breakdownByVehicle
    ) {
    }

    public static record PlatformFee(
            @JsonProperty("total") BigDecimal total,
            @JsonProperty("effective_rate") BigDecimal effectiveRate
    ) {
    }

    public static record Compare(
            @JsonProperty("gross_booking_value_change_percent") BigDecimal gbvChange,
            @JsonProperty("platform_fee_change_percent") BigDecimal feeChange,
            @JsonProperty("contribution_change_percent") BigDecimal contribChange
    ) {
    }

    public static record TrendPoint(
            @JsonProperty("bucket") String bucket,
            @JsonProperty("gross_booking_value") BigDecimal gbv,
            @JsonProperty("platform_fee") BigDecimal platformFee
    ) {
    }

    public static record DataQualityWarning(
            @JsonProperty("field") String field,
            @JsonProperty("code") String code
    ) {
    }
}
