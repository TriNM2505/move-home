package vn.movehome.backend.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.vnpay")
@Getter
@Setter
public class VnPayProperties {

    private String tmnCode;
    private String hashSecret;
    private String paymentUrl;
    private String returnUrl;
    private String ipnUrl;
}
