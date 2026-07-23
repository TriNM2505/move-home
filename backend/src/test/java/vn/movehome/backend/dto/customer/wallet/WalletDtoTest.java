package vn.movehome.backend.dto.customer.wallet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletDtoTest {

    @Test
    void transactionDtoExposesAllFields() {
        UUID id = UUID.randomUUID();
        UUID relatedOrderId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-06-01T08:00:00Z");
        TransactionDTO dto = new TransactionDTO(
                id, "TOPUP", new BigDecimal("500000"), new BigDecimal("1500000"),
                relatedOrderId, "Nạp tiền vào ví", "VNP****1234", createdAt);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.type()).isEqualTo("TOPUP");
        assertThat(dto.amount()).isEqualTo(new BigDecimal("500000"));
        assertThat(dto.balanceAfter()).isEqualTo(new BigDecimal("1500000"));
        assertThat(dto.relatedOrderId()).isEqualTo(relatedOrderId);
        assertThat(dto.description()).isEqualTo("Nạp tiền vào ví");
        assertThat(dto.vnpayTxnRefMasked()).isEqualTo("VNP****1234");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void walletSummaryDtoExposesAllFields() {
        WalletSummaryDTO dto = new WalletSummaryDTO(
                new BigDecimal("1000000"), new BigDecimal("3000000"),
                new BigDecimal("2000000"), new BigDecimal("500000"));

        assertThat(dto.balance()).isEqualTo(new BigDecimal("1000000"));
        assertThat(dto.totalToppedUp()).isEqualTo(new BigDecimal("3000000"));
        assertThat(dto.totalSpent()).isEqualTo(new BigDecimal("2000000"));
        assertThat(dto.totalWithdrawn()).isEqualTo(new BigDecimal("500000"));
    }
}
