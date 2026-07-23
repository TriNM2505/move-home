package vn.movehome.backend.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreateWalletTopUpRequestTest {

    @Test
    void exposesAmount() {
        CreateWalletTopUpRequest request = new CreateWalletTopUpRequest(new BigDecimal("500000"));

        assertThat(request.amount()).isEqualByComparingTo("500000");
    }
}
