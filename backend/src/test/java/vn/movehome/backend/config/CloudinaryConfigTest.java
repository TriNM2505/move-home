package vn.movehome.backend.config;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CloudinaryConfigTest {

    @Test
    void cloudinaryDoesNotThrowWhenCloudinaryUrlIsMissing() {
        Cloudinary cloudinary = assertDoesNotThrow(() -> new CloudinaryConfig().cloudinary());

        assertNotNull(cloudinary);
    }

    @Test
    void cloudinaryReturnsConfiguredInstanceWhenAllCredentialsPresent() throws Exception {
        CloudinaryConfig config = new CloudinaryConfig();
        setField(config, "cloudName", "movehome-demo");
        setField(config, "apiKey", "123456789");
        setField(config, "apiSecret", "s3cr3t");

        Cloudinary cloudinary = config.cloudinary();

        assertThat(cloudinary.config.cloudName).isEqualTo("movehome-demo");
        assertThat(cloudinary.config.apiKey).isEqualTo("123456789");
        assertThat(cloudinary.config.apiSecret).isEqualTo("s3cr3t");
        assertThat(cloudinary.config.secure).isTrue();
    }

    @Test
    void cloudinaryReturnsEmptyInstanceWhenOnlyApiSecretIsMissing() throws Exception {
        CloudinaryConfig config = new CloudinaryConfig();
        setField(config, "cloudName", "movehome-demo");
        setField(config, "apiKey", "123456789");
        setField(config, "apiSecret", "");

        Cloudinary cloudinary = config.cloudinary();

        assertThat(cloudinary.config.cloudName).isNullOrEmpty();
        assertThat(cloudinary.config.apiKey).isNullOrEmpty();
    }

    @Test
    void cloudinaryReturnsEmptyInstanceWhenApiKeyIsBlank() throws Exception {
        CloudinaryConfig config = new CloudinaryConfig();
        setField(config, "cloudName", "movehome-demo");
        setField(config, "apiKey", "   ");
        setField(config, "apiSecret", "s3cr3t");

        Cloudinary cloudinary = config.cloudinary();

        assertThat(cloudinary.config.cloudName).isNullOrEmpty();
    }

    @Test
    void cloudinaryReturnsEmptyInstanceWhenCloudNameIsMissing() throws Exception {
        CloudinaryConfig config = new CloudinaryConfig();
        setField(config, "cloudName", "");
        setField(config, "apiKey", "123456789");
        setField(config, "apiSecret", "s3cr3t");

        Cloudinary cloudinary = config.cloudinary();

        assertThat(cloudinary.config.apiKey).isNullOrEmpty();
    }

    private void setField(Object target, String fieldName, String value) throws Exception {
        Field field = CloudinaryConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
