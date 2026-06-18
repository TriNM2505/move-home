package vn.movehome.backend.config;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CloudinaryConfigTest {

    @Test
    void cloudinaryDoesNotThrowWhenCloudinaryUrlIsMissing() {
        Cloudinary cloudinary = assertDoesNotThrow(() -> new CloudinaryConfig().cloudinary());

        assertNotNull(cloudinary);
    }
}
