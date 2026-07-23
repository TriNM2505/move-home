package vn.movehome.backend.dispute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriverPenaltyResponseTest {

    @Test
    void recordExposesAllFields() {
        UUID disputeId = UUID.randomUUID();
        OffsetDateTime deadline = OffsetDateTime.parse("2026-07-01T10:02:00Z");

        DriverPenaltyResponse response = new DriverPenaltyResponse(
                disputeId, "ORD-003", new BigDecimal("500000"), deadline);

        assertThat(response.disputeId()).isEqualTo(disputeId);
        assertThat(response.orderCode()).isEqualTo("ORD-003");
        assertThat(response.shortfall()).isEqualByComparingTo("500000");
        assertThat(response.deadline()).isEqualTo(deadline);
    }
}
