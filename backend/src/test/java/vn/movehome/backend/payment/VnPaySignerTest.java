package vn.movehome.backend.payment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void signThrowsWhenHashSecretIsMissing() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TmnCode", "DEMO");

        assertThatThrownBy(() -> signer.sign(fields, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign(fields, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyReturnsFalseWhenSecureHashMissingOrBlank() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TmnCode", "DEMO");

        assertThat(signer.verify(fields, "sandbox-secret")).isFalse();

        fields.put("vnp_SecureHash", "   ");
        assertThat(signer.verify(fields, "sandbox-secret")).isFalse();
    }

    @Test
    void buildSignedUrlSkipsNullAndBlankFieldsWhenSigning() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TmnCode", "DEMO");
        fields.put("vnp_Empty", "");
        fields.put("vnp_Blank", "   ");
        fields.put("vnp_Null", null);
        fields.put(null, "ignored-null-key");

        String url = signer.buildSignedUrl("https://sandbox.vnpayment.vn/pay", fields, "sandbox-secret");

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/pay?vnp_TmnCode=DEMO&vnp_SecureHash=");
        assertThat(url).doesNotContain("vnp_Empty").doesNotContain("vnp_Blank").doesNotContain("vnp_Null");

        // Chu ky chi tinh tren field con hieu luc (vnp_TmnCode) — khop voi sign() tren map da loc san
        Map<String, String> onlyRelevant = new LinkedHashMap<>();
        onlyRelevant.put("vnp_TmnCode", "DEMO");
        assertThat(signer.sign(fields, "sandbox-secret")).isEqualTo(signer.sign(onlyRelevant, "sandbox-secret"));
    }
}
