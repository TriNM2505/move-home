package vn.movehome.backend.dispute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveDeductRequestTest {

    @Test
    void recordExposesAllFields() {
        ResolveDeductRequest request = new ResolveDeductRequest(
                new BigDecimal("300000"), "Tru vi tai xe do lam vo hang hoa cua khach hang");

        assertThat(request.deductAmount()).isEqualByComparingTo("300000");
        assertThat(request.note()).isEqualTo("Tru vi tai xe do lam vo hang hoa cua khach hang");
    }
}
