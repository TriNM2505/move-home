package vn.movehome.backend.dto.admin.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomersReportResponse(
        @JsonProperty("period") FinancialReportResponse.Period period,
        @JsonProperty("total_customers_at_period_end") long totalCustomersAtPeriodEnd,
        @JsonProperty("active_users") ActiveUsers activeUsers,
        @JsonProperty("retention_rate_30d")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        BigDecimal retentionRate30d,
        @JsonProperty("top_spenders") List<TopSpenderItem> topSpenders,
        @JsonProperty("average_spend_per_paying_customer") BigDecimal averageSpendPerPayingCustomer,
        @JsonProperty("new_customers_in_period") long newCustomersInPeriod,
        @JsonProperty("compare") Compare compare,
        @JsonProperty("data_quality") List<FinancialReportResponse.DataQualityWarning> dataQuality
) {
    public static record ActiveUsers(
            @JsonProperty("dau_average") BigDecimal dauAverage,
            @JsonProperty("mau") long mau
    ) {
    }

    public static record TopSpenderItem(
            @JsonProperty("customer_id") UUID customerId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("completed_orders") long completedOrders,
            @JsonProperty("total_spent") BigDecimal totalSpent
    ) {
    }

    public static record Compare(
            @JsonProperty("new_customers_change_percent") BigDecimal newCustomersChange,
            @JsonProperty("retention_change_percent") BigDecimal retentionChange
    ) {
    }
}
