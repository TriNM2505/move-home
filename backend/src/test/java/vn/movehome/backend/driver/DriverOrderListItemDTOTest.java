package vn.movehome.backend.driver;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriverOrderListItemDTOTest {

    @Test
    void recordExposesAllComponentsAndSupportsEqualsHashCodeToString() {
        UUID id = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        DriverOrderListItemDTO dto = new DriverOrderListItemDTO(
                id, "MH-0001", "CONFIRMED", "TRUCK_500KG",
                "Cầu Giấy", "Đống Đa", scheduledAt,
                new BigDecimal("5.50"), new BigDecimal("500000"),
                createdAt, new BigDecimal("0.3000"));

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.orderCode()).isEqualTo("MH-0001");
        assertThat(dto.status()).isEqualTo("CONFIRMED");
        assertThat(dto.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(dto.pickupDistrict()).isEqualTo("Cầu Giấy");
        assertThat(dto.dropoffDistrict()).isEqualTo("Đống Đa");
        assertThat(dto.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(dto.distanceKm()).isEqualByComparingTo("5.50");
        assertThat(dto.totalQuote()).isEqualByComparingTo("500000");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
        assertThat(dto.commissionRateSnapshot()).isEqualByComparingTo("0.3000");

        DriverOrderListItemDTO same = new DriverOrderListItemDTO(
                id, "MH-0001", "CONFIRMED", "TRUCK_500KG",
                "Cầu Giấy", "Đống Đa", scheduledAt,
                new BigDecimal("5.50"), new BigDecimal("500000"),
                createdAt, new BigDecimal("0.3000"));
        DriverOrderListItemDTO different = new DriverOrderListItemDTO(
                UUID.randomUUID(), "MH-0002", "ACCEPTED", "TRUCK_1TAN",
                "Ba Đình", "Hai Bà Trưng", scheduledAt,
                new BigDecimal("10.00"), new BigDecimal("900000"),
                createdAt, new BigDecimal("0.3000"));

        assertThat(dto).isEqualTo(same);
        assertThat(dto.hashCode()).isEqualTo(same.hashCode());
        assertThat(dto).isNotEqualTo(different);
        assertThat(dto.toString()).contains("MH-0001");
    }
}
