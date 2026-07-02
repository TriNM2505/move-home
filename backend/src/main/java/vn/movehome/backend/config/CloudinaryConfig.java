package vn.movehome.backend.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

/**
 * Cau hinh Cloudinary tu 3 bien moi truong rieng: cloud_name, api_key,
 * api_secret.
 * HR-01: khong hardcode credential trong source code.
 * Doc qua namespace app.cloudinary.* (resolve tu env CLOUDINARY_* qua
 * application.properties).
 * Neu 1 trong 3 thieu -> log warn + tra ve Cloudinary empty (upload se fail
 * som).
 */
@Configuration
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        if (!StringUtils.hasText(cloudName)
                || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(apiSecret)) {
            log.warn(
                    "Cloudinary credentials chua day du (cloud_name/api_key/api_secret) — chuc nang upload anh se chua hoat dong");
            return new Cloudinary(ObjectUtils.emptyMap());
        }

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", true);

        return new Cloudinary(config);
    }
}