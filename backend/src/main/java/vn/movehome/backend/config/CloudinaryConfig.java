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
 * Neu 1 trong 3 bien thieu -> log warn + tra ve Cloudinary empty (upload se
 * fail som).
 */
@Configuration
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Value("${CLOUDINARY_CLOUD_NAME:}")
    private String cloudName;

    @Value("${CLOUDINARY_API_KEY:}")
    private String apiKey;

    @Value("${CLOUDINARY_API_SECRET:}")
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