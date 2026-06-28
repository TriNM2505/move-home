package vn.movehome.backend.dto.admin.detail;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerDetailResponse(
        @JsonProperty("user") UserSection user,
        @JsonProperty("stats") StatsSection stats,
        @JsonProperty("recent_orders") List<RecentOrderItem> recentOrders,
        @JsonProperty("wallet_summary") WalletSummary walletSummary,
        @JsonProperty("recent_wallet_transactions") List<RecentWalletTransactionItem> recentWalletTransactions,
        @JsonProperty("dispute_history_preview") List<DisputePreviewItem> disputeHistoryPreview,
        @JsonProperty("district_activity") List<DistrictActivityItem> districtActivity,
        @JsonProperty("login_history") List<LoginHistoryItem> loginHistory,
        @JsonProperty("allowed_actions") List<String> allowedActions
) {
    public static record UserSection(
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("email") String email,
            @JsonProperty("phone_masked") String phoneMasked,
            @JsonProperty("status") String status,
            @JsonProperty("email_verified") boolean emailVerified,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("last_login_at") OffsetDateTime lastLoginAt
    ) {
    }

    public static record StatsSection(
            @JsonProperty("total_orders") long totalOrders,
            @JsonProperty("total_completed") long totalCompleted,
            @JsonProperty("total_cancelled") long totalCancelled,
            @JsonProperty("total_dispute_count") long totalDisputeCount,
            @JsonProperty("total_spent") BigDecimal totalSpent,
            @JsonProperty("first_order_at") OffsetDateTime firstOrderAt,
            @JsonProperty("last_order_at") OffsetDateTime lastOrderAt
    ) {
    }

    public static record RecentOrderItem(
            @JsonProperty("id") UUID id,
            @JsonProperty("order_code") String orderCode,
            @JsonProperty("status") String status,
            @JsonProperty("pickup_district") String pickupDistrict,
            @JsonProperty("dropoff_district") String dropoffDistrict,
            @JsonProperty("total_quote") BigDecimal totalQuote,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {
    }

    public static record WalletSummary(
            @JsonProperty("balance") BigDecimal balance,
            @JsonProperty("total_topped_up") BigDecimal totalToppedUp,
            @JsonProperty("total_spent") BigDecimal totalSpent
    ) {
    }

    public static record RecentWalletTransactionItem(
            @JsonProperty("id") UUID id,
            @JsonProperty("type") String type,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("balance_after") BigDecimal balanceAfter,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("reference_masked") String referenceMasked
    ) {
    }

    public static record DisputePreviewItem(
            @JsonProperty("id") UUID id,
            @JsonProperty("order_code") String orderCode,
            @JsonProperty("status") String status,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {
    }

    public static record DistrictActivityItem(
            @JsonProperty("district") String district,
            @JsonProperty("pickup_count") long pickupCount,
            @JsonProperty("dropoff_count") long dropoffCount
    ) {
    }

    public static record LoginHistoryItem(
            @JsonProperty("logged_in_at") OffsetDateTime loggedInAt,
            @JsonProperty("ip_masked") String ipMasked,
            @JsonProperty("user_agent_summary") String userAgentSummary
    ) {
    }
}
