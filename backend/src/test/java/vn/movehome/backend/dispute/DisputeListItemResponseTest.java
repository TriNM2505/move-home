package vn.movehome.backend.dispute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeListItemResponseTest {

    @Test
    void recordExposesAllFields() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");
        OffsetDateTime deadline = OffsetDateTime.parse("2026-07-02T10:00:00Z");

        DisputeListItemResponse response = new DisputeListItemResponse(
                id, orderId, "ORD-002", "COMPLETED",
                customerId, "Nguyen Van A", driverId, "Tran Van B",
                "ITEM_DAMAGED", new BigDecimal("500000"), "OPEN", createdAt, deadline);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderCode()).isEqualTo("ORD-002");
        assertThat(response.orderStatus()).isEqualTo("COMPLETED");
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.customerName()).isEqualTo("Nguyen Van A");
        assertThat(response.driverId()).isEqualTo(driverId);
        assertThat(response.driverName()).isEqualTo("Tran Van B");
        assertThat(response.claimType()).isEqualTo("ITEM_DAMAGED");
        assertThat(response.claimAmount()).isEqualByComparingTo("500000");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.deadline()).isEqualTo(deadline);
    }
}
