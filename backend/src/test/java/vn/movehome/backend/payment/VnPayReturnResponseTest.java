package vn.movehome.backend.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VnPayReturnResponseTest {

    @Test
    void exposesAllFields() {
        VnPayReturnResponse response = new VnPayReturnResponse(
                true,
                true,
                "ORD-abc",
                "00",
                "Thanh toan VNPay thanh cong.",
                "PROCESSED");

        assertThat(response.signatureValid()).isTrue();
        assertThat(response.successful()).isTrue();
        assertThat(response.txnRef()).isEqualTo("ORD-abc");
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.message()).isEqualTo("Thanh toan VNPay thanh cong.");
        assertThat(response.processingStatus()).isEqualTo("PROCESSED");
    }
}
