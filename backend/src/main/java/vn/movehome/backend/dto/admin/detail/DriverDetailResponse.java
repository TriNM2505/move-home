package vn.movehome.backend.dto.admin.detail;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DriverDetailResponse(
        @JsonProperty("user") UserSection user,
        @JsonProperty("profile") ProfileSection profile,
        @JsonProperty("documents_summary") DocumentsSummary documentsSummary,
        @JsonProperty("vehicles") List<VehicleItem> vehicles,
        @JsonProperty("deposit") DepositSection deposit,
        @JsonProperty("wallet") WalletSection wallet,
        @JsonProperty("stats") StatsSection stats,
        @JsonProperty("rating_distribution") RatingDistribution ratingDistribution,
        @JsonProperty("recent_orders") List<RecentOrderItem> recentOrders,
        @JsonProperty("recent_withdrawals") List<RecentWithdrawalItem> recentWithdrawals,
        @JsonProperty("online_status") String onlineStatus,
        @JsonProperty("last_known_location") LastKnownLocation lastKnownLocation,
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

    public static record ProfileSection(
            @JsonProperty("license_number") String licenseNumber,
            @JsonProperty("license_class") String licenseClass,
            @JsonProperty("license_expiry_date") LocalDate licenseExpiryDate,
            @JsonProperty("onboarding_completed_at") OffsetDateTime onboardingCompletedAt,
            @JsonProperty("approved_at") OffsetDateTime approvedAt
    ) {
    }

    public static record DocumentsSummary(
            @JsonProperty("driving_license_count") long drivingLicenseCount,
            @JsonProperty("vehicle_registration_count") long vehicleRegistrationCount,
            @JsonProperty("vehicle_photo_count") long vehiclePhotoCount,
            @JsonProperty("total_count") long totalCount
    ) {
    }

    public static record VehicleItem(
            @JsonProperty("plate") String plate,
            @JsonProperty("vehicle_type") String vehicleType,
            @JsonProperty("capacity_kg") Integer capacityKg,
            @JsonProperty("is_primary") boolean isPrimary
    ) {
    }

    public static record DepositSection(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("paid_at") OffsetDateTime paidAt,
            @JsonProperty("status") String status
    ) {
    }

    public static record WalletSection(
            @JsonProperty("balance") BigDecimal balance,
            @JsonProperty("total_earned") BigDecimal totalEarned,
            @JsonProperty("total_withdrawn") BigDecimal totalWithdrawn
    ) {
    }

    public static record StatsSection(
            @JsonProperty("total_completed_orders") long totalCompletedOrders,
            @JsonProperty("total_cancelled_orders") long totalCancelledOrders,
            @JsonProperty("total_dispute_count") long totalDisputeCount,
            @JsonProperty("average_rating") BigDecimal averageRating,
            @JsonProperty("total_ratings_count") long totalRatingsCount
    ) {
    }

    public static record RatingDistribution(
            @JsonProperty("one_star") long oneStar,
            @JsonProperty("two_star") long twoStar,
            @JsonProperty("three_star") long threeStar,
            @JsonProperty("four_star") long fourStar,
            @JsonProperty("five_star") long fiveStar,
            @JsonProperty("total") long total,
            @JsonProperty("average") BigDecimal average
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

    public static record RecentWithdrawalItem(
            @JsonProperty("id") UUID id,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("status") String status,
            @JsonProperty("requested_at") OffsetDateTime requestedAt,
            @JsonProperty("processed_at") OffsetDateTime processedAt
    ) {
    }

    public static record LastKnownLocation(
            @JsonProperty("lat") BigDecimal lat,
            @JsonProperty("lng") BigDecimal lng,
            @JsonProperty("updated_at") OffsetDateTime updatedAt
    ) {
    }
}
