package vn.movehome.backend.dto.admin.list;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO thuan tuy (record) — CustomerListItem/DriverListItem co 2 constructor: mot constructor
 * "chinh" (dung boi Service test hien co, nhan String/OffsetDateTime da convert san) va mot
 * constructor phu convert truc tiep tu UserStatus/Instant (dung boi JPQL "new ...()" projection).
 * Test nay goi rieng constructor phu de dat instruction coverage day du, bao gom nhanh
 * status == null va Instant == null cua toOffsetDateTime.
 */
class AdminListDtoTest {

    @Test
    void customerListItemSecondaryConstructorConvertsStatusAndInstantFields() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant lastActiveAt = Instant.parse("2026-06-20T00:00:00Z");

        CustomerListItem withStatusAndDates = new CustomerListItem(
                userId, "Nguyen Van A", "customer@movehome.vn", "0900000000",
                UserStatus.ACTIVE, 5L, new BigDecimal("100000"), new BigDecimal("50000"),
                createdAt, lastActiveAt, true);

        assertThat(withStatusAndDates.userId()).isEqualTo(userId);
        assertThat(withStatusAndDates.fullName()).isEqualTo("Nguyen Van A");
        assertThat(withStatusAndDates.email()).isEqualTo("customer@movehome.vn");
        assertThat(withStatusAndDates.phone()).isEqualTo("0900000000");
        assertThat(withStatusAndDates.status()).isEqualTo("ACTIVE");
        assertThat(withStatusAndDates.totalOrders()).isEqualTo(5L);
        assertThat(withStatusAndDates.totalSpent()).isEqualByComparingTo("100000");
        assertThat(withStatusAndDates.walletBalance()).isEqualByComparingTo("50000");
        assertThat(withStatusAndDates.createdAt()).isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        assertThat(withStatusAndDates.lastActiveAt()).isEqualTo(OffsetDateTime.ofInstant(lastActiveAt, ZoneOffset.UTC));
        assertThat(withStatusAndDates.emailVerified()).isTrue();

        CustomerListItem withNullStatusAndDates = new CustomerListItem(
                userId, "Nguyen Van B", "b@movehome.vn", "0900000001",
                (UserStatus) null, 0L, BigDecimal.ZERO, BigDecimal.ZERO,
                (Instant) null, null, false);

        assertThat(withNullStatusAndDates.status()).isNull();
        assertThat(withNullStatusAndDates.createdAt()).isNull();
        assertThat(withNullStatusAndDates.lastActiveAt()).isNull();
        assertThat(withNullStatusAndDates.emailVerified()).isFalse();
    }

    @Test
    void driverListItemSecondaryConstructorConvertsStatusInstantAndCountFields() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant lastActiveAt = Instant.parse("2026-06-20T00:00:00Z");

        DriverListItem withValues = new DriverListItem(
                userId, "Tran Van C", "driver@movehome.vn", "0911111111",
                "TRUCK_500KG", "51A-12345", UserStatus.ACTIVE, new BigDecimal("4.80"),
                12, new BigDecimal("4500000"), new BigDecimal("900000"), createdAt, lastActiveAt);

        assertThat(withValues.userId()).isEqualTo(userId);
        assertThat(withValues.fullName()).isEqualTo("Tran Van C");
        assertThat(withValues.email()).isEqualTo("driver@movehome.vn");
        assertThat(withValues.phone()).isEqualTo("0911111111");
        assertThat(withValues.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(withValues.licensePlate()).isEqualTo("51A-12345");
        assertThat(withValues.status()).isEqualTo("ACTIVE");
        assertThat(withValues.averageRating()).isEqualByComparingTo("4.80");
        assertThat(withValues.totalCompletedOrders()).isEqualTo(12L);
        assertThat(withValues.totalEarnings()).isEqualByComparingTo("4500000");
        assertThat(withValues.currentBalance()).isEqualByComparingTo("900000");
        assertThat(withValues.createdAt()).isEqualTo(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        assertThat(withValues.lastActiveAt()).isEqualTo(OffsetDateTime.ofInstant(lastActiveAt, ZoneOffset.UTC));

        DriverListItem withNulls = new DriverListItem(
                userId, "Tran Van D", "driver2@movehome.vn", "0911111112",
                "TRUCK_500KG", null, (UserStatus) null, null,
                (Integer) null, null, null, (Instant) null, null);

        assertThat(withNulls.status()).isNull();
        assertThat(withNulls.totalCompletedOrders()).isNull();
        assertThat(withNulls.createdAt()).isNull();
        assertThat(withNulls.lastActiveAt()).isNull();
    }
}
