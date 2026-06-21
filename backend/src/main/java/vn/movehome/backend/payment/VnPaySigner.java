package vn.movehome.backend.payment;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class VnPaySigner {

    private static final String HMAC_SHA512 = "HmacSHA512";
    private static final String SECURE_HASH = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    public String buildSignedUrl(String paymentUrl, Map<String, String> fields, String hashSecret) {
        String secureHash = sign(fields, hashSecret);
        String query = sortedSignableFields(fields)
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return paymentUrl + "?" + query + "&" + SECURE_HASH + "=" + secureHash;
    }

    public boolean verify(Map<String, String> fields, String hashSecret) {
        String providedHash = fields.get(SECURE_HASH);
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }

        String expectedHash = sign(fields, hashSecret);
        byte[] expected = expectedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    public String sign(Map<String, String> fields, String hashSecret) {
        if (hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalArgumentException("VNPay hash secret is not configured");
        }
        String hashData = sortedSignableFields(fields)
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return hmacSha512(hashSecret, hashData);
    }

    private TreeMap<String, String> sortedSignableFields(Map<String, String> fields) {
        TreeMap<String, String> sorted = new TreeMap<>();
        fields.forEach((key, value) -> {
            if (key == null || value == null || value.isBlank()) {
                return;
            }
            if (SECURE_HASH.equals(key) || SECURE_HASH_TYPE.equals(key)) {
                return;
            }
            sorted.put(key, value);
        });
        return sorted;
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign VNPay payload", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
