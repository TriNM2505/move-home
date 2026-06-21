package vn.movehome.backend.payment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnPaySignerTest {

    private final VnPaySigner signer = new VnPaySigner();

    @Test
    void signedUrlCanBeVerifiedAndTamperingFails() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", "DEMO");
        fields.put("vnp_Amount", "50000000");
        fields.put("vnp_TxnRef", "ORD-abc");
        fields.put("vnp_OrderInfo", "MoveHome order MH001");

        String hash = signer.sign(fields, "sandbox-secret");
        fields.put("vnp_SecureHash", hash);

        assertThat(signer.verify(fields, "sandbox-secret")).isTrue();

        fields.put("vnp_Amount", "90000000");

        assertThat(signer.verify(fields, "sandbox-secret")).isFalse();
    }
}
