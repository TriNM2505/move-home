package vn.movehome.backend.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VnPayPaymentExceptionTest {

    @Test
    void exposesRspCodeAndMessage() {
        VnPayPaymentException exception = new VnPayPaymentException("04", "Invalid amount");

        assertThat(exception.rspCode()).isEqualTo("04");
        assertThat(exception.getMessage()).isEqualTo("Invalid amount");
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
