package vn.movehome.backend.dispute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeDetailResponseTest {

    @Test
    void recordExposesAllFieldsIncludingNestedOrderAndPartySummaries() {
        UUID disputeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID resolvedBy = UUID.randomUUID();
        OffsetDateTime driverResponseAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");
        OffsetDateTime resolvedAt = OffsetDateTime.parse("2026-07-01T12:00:00Z");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-07-02T09:00:00Z");
        OffsetDateTime deductDeadline = OffsetDateTime.parse("2026-07-01T13:00:00Z");
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-29T08:00:00Z");
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-06-30T08:00:00Z");

        DisputeDetailResponse.OrderSummary order = new DisputeDetailResponse.OrderSummary(
                orderId, "ORD-001", "COMPLETED", new BigDecimal("2000000"),
                scheduledAt, completedAt, "123 Le Loi, Q1", "456 Nguyen Trai, Q5", "TRUCK_1T5");
        DisputeDetailResponse.PartySummary customer = new DisputeDetailResponse.PartySummary(
                customerId, "Nguyen Van A", "0900000001");
        DisputeDetailResponse.PartySummary driver = new DisputeDetailResponse.PartySummary(
                driverId, "Tran Van B", "0900000002");

        DisputeDetailResponse response = new DisputeDetailResponse(
                disputeId,
                "OPEN",
                "ITEM_DAMAGED",
                new BigDecimal("500000"),
                "Do dac bi vo trong qua trinh van chuyen",
                "Toi khong lam vo do dac",
                driverResponseAt,
                new BigDecimal("400000"),
                "Da xac minh qua camera",
                resolvedBy,
                resolvedAt,
                createdAt,
                deadline,
                new BigDecimal("100000"),
                deductDeadline,
                List.of("https://res.cloudinary.com/demo/photo1.jpg"),
                order,
                customer,
                driver);

        assertThat(response.id()).isEqualTo(disputeId);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.claimType()).isEqualTo("ITEM_DAMAGED");
        assertThat(response.claimAmount()).isEqualByComparingTo("500000");
        assertThat(response.customerStatement()).isEqualTo("Do dac bi vo trong qua trinh van chuyen");
        assertThat(response.driverResponse()).isEqualTo("Toi khong lam vo do dac");
        assertThat(response.driverResponseAt()).isEqualTo(driverResponseAt);
        assertThat(response.resolutionAmount()).isEqualByComparingTo("400000");
        assertThat(response.resolutionNote()).isEqualTo("Da xac minh qua camera");
        assertThat(response.resolvedBy()).isEqualTo(resolvedBy);
        assertThat(response.resolvedAt()).isEqualTo(resolvedAt);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.deadline()).isEqualTo(deadline);
        assertThat(response.pendingDeductShortfall()).isEqualByComparingTo("100000");
        assertThat(response.deductDeadline()).isEqualTo(deductDeadline);
        assertThat(response.photoUrls()).containsExactly("https://res.cloudinary.com/demo/photo1.jpg");
        assertThat(response.order()).isEqualTo(order);
        assertThat(response.customer()).isEqualTo(customer);
        assertThat(response.driver()).isEqualTo(driver);

        assertThat(order.id()).isEqualTo(orderId);
        assertThat(order.orderCode()).isEqualTo("ORD-001");
        assertThat(order.status()).isEqualTo("COMPLETED");
        assertThat(order.totalQuote()).isEqualByComparingTo("2000000");
        assertThat(order.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(order.completedAt()).isEqualTo(completedAt);
        assertThat(order.pickupAddress()).isEqualTo("123 Le Loi, Q1");
        assertThat(order.dropoffAddress()).isEqualTo("456 Nguyen Trai, Q5");
        assertThat(order.vehicleType()).isEqualTo("TRUCK_1T5");

        assertThat(customer.id()).isEqualTo(customerId);
        assertThat(customer.fullName()).isEqualTo("Nguyen Van A");
        assertThat(customer.phone()).isEqualTo("0900000001");

        assertThat(driver.id()).isEqualTo(driverId);
        assertThat(driver.fullName()).isEqualTo("Tran Van B");
        assertThat(driver.phone()).isEqualTo("0900000002");
    }
}
