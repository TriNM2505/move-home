package vn.movehome.backend.driver;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriverOrderDetailDTOTest {

    private DriverOrderDetailDTO buildDto(UUID id, UUID driverId, OffsetDateTime now) {
        return new DriverOrderDetailDTO(
                id,
                "MH-0001",
                "ACCEPTED",
                false,
                true,
                true,
                "Nguyễn Văn A",
                "+84912345678",
                "TRUCK_500KG",
                2,
                "123 Đường A",
                "Cầu Giấy",
                new BigDecimal("21.0300000"),
                new BigDecimal("105.8000000"),
                "456 Đường B",
                "Đống Đa",
                new BigDecimal("21.0100000"),
                new BigDecimal("105.8200000"),
                now,
                new BigDecimal("5.50"),
                30,
                new BigDecimal("200000"),
                new BigDecimal("50000"),
                new BigDecimal("30000"),
                new BigDecimal("20000"),
                new BigDecimal("100000"),
                new BigDecimal("400000"),
                "Ghi chú test",
                driverId,
                now.minusDays(1),
                now,
                null,
                null,
                null,
                new BigDecimal("0.3000"),
                now.minusMinutes(10));
    }

    @Test
    void recordExposesAllComponentsAndSupportsEqualsHashCodeToString() {
        UUID id = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DriverOrderDetailDTO dto = buildDto(id, driverId, now);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.orderCode()).isEqualTo("MH-0001");
        assertThat(dto.status()).isEqualTo("ACCEPTED");
        assertThat(dto.available()).isFalse();
        assertThat(dto.assignedToCurrentDriver()).isTrue();
        assertThat(dto.finalPaid()).isTrue();
        assertThat(dto.customerName()).isEqualTo("Nguyễn Văn A");
        assertThat(dto.customerPhone()).isEqualTo("+84912345678");
        assertThat(dto.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(dto.porterCount()).isEqualTo(2);
        assertThat(dto.pickupAddress()).isEqualTo("123 Đường A");
        assertThat(dto.pickupDistrict()).isEqualTo("Cầu Giấy");
        assertThat(dto.pickupLat()).isEqualByComparingTo("21.0300000");
        assertThat(dto.pickupLng()).isEqualByComparingTo("105.8000000");
        assertThat(dto.dropoffAddress()).isEqualTo("456 Đường B");
        assertThat(dto.dropoffDistrict()).isEqualTo("Đống Đa");
        assertThat(dto.dropoffLat()).isEqualByComparingTo("21.0100000");
        assertThat(dto.dropoffLng()).isEqualByComparingTo("105.8200000");
        assertThat(dto.scheduledAt()).isEqualTo(now);
        assertThat(dto.distanceKm()).isEqualByComparingTo("5.50");
        assertThat(dto.estimatedDurationMinutes()).isEqualTo(30);
        assertThat(dto.baseFare()).isEqualByComparingTo("200000");
        assertThat(dto.peakSurcharge()).isEqualByComparingTo("50000");
        assertThat(dto.alleySurcharge()).isEqualByComparingTo("30000");
        assertThat(dto.floorSurcharge()).isEqualByComparingTo("20000");
        assertThat(dto.porterFee()).isEqualByComparingTo("100000");
        assertThat(dto.totalQuote()).isEqualByComparingTo("400000");
        assertThat(dto.notes()).isEqualTo("Ghi chú test");
        assertThat(dto.driverId()).isEqualTo(driverId);
        assertThat(dto.createdAt()).isEqualTo(now.minusDays(1));
        assertThat(dto.updatedAt()).isEqualTo(now);
        assertThat(dto.completedAt()).isNull();
        assertThat(dto.cancelledAt()).isNull();
        assertThat(dto.cancellationReason()).isNull();
        assertThat(dto.commissionRateSnapshot()).isEqualByComparingTo("0.3000");
        assertThat(dto.arrivedAt()).isEqualTo(now.minusMinutes(10));

        DriverOrderDetailDTO same = buildDto(id, driverId, now);
        DriverOrderDetailDTO different = buildDto(UUID.randomUUID(), driverId, now);

        assertThat(dto).isEqualTo(same);
        assertThat(dto.hashCode()).isEqualTo(same.hashCode());
        assertThat(dto).isNotEqualTo(different);
        assertThat(dto.toString()).contains("MH-0001");
    }
}
