package vn.movehome.backend.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Cau hinh Cloudinary tu bien moi truong CLOUDINARY_URL.
 * HR-01: khong hardcode credential trong source code.
 */
@Configuration
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Bean
    public Cloudinary cloudinary() {
        String cloudinaryUrl = System.getenv("CLOUDINARY_URL");
        if (!StringUtils.hasText(cloudinaryUrl)) {
            log.warn("CLOUDINARY_URL chưa cấu hình — chức năng upload ảnh sẽ chưa hoạt động");
            return new Cloudinary(ObjectUtils.emptyMap());
        }
        return new Cloudinary(cloudinaryUrl);
    }
}
