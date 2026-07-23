package vn.movehome.backend.chat;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatImageServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private ChatImageService service;

    @BeforeEach
    void setUp() {
        service = new ChatImageService(cloudinary);
    }

    @Test
    void uploadStoresJpegImageInConversationFolderAndReturnsPublicId() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();
        String publicId = "movehome/chat/%s/abc".formatted(conversationId);

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("public_id", publicId));

        String result = service.upload(conversationId, file);

        assertThat(result).isEqualTo(publicId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertThat(options.get("folder")).isEqualTo("movehome/chat/%s".formatted(conversationId));
        assertThat(options.get("resource_type")).isEqualTo("image");
        assertThat(options.get("type")).isEqualTo("authenticated");
    }

    @Test
    void uploadAcceptsValidPngImage() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "anh.png", "image/png", validPngBytes());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("public_id", "pid-png"));

        String result = service.upload(conversationId, file);

        assertThat(result).isEqualTo("pid-png");
    }

    @Test
    void uploadAcceptsValidWebpImage() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "anh.webp", "image/webp", validWebpBytes());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("public_id", "pid-webp"));

        String result = service.upload(conversationId, file);

        assertThat(result).isEqualTo("pid-webp");
    }

    @Test
    void uploadRejectsEmptyFile() {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.upload(conversationId, empty))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
    }

    @Test
    void uploadRejectsNullFile() {
        UUID conversationId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(conversationId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống."));
    }

    @Test
    void uploadRejectsFileExceedingMaxSize() {
        UUID conversationId = UUID.randomUUID();
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(ChatImageService.MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> service.upload(conversationId, oversized))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason())
                            .isEqualTo("INVALID_FILE|Kích thước ảnh không được vượt quá 1,5 MB.");
                });
    }

    @Test
    void uploadRejectsFileThatCannotBeRead() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(10L);
        when(unreadable.getBytes()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.upload(conversationId, unreadable))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên."));
    }

    @Test
    void uploadRejectsUnsupportedFileContent() {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "không phải ảnh".getBytes());

        assertThatThrownBy(() -> service.upload(conversationId, fakeImage))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ."));
    }

    @Test
    void uploadRejectsFileShorterThanPngSignatureLength() {
        // Byte array qua ngan (< 8 byte) de kich hoat nhanh "return false" som trong isPng()
        // (khac voi test tren dung chuoi dai hon 8 byte roi moi mismatch trong vong lap).
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile tooShort = new MockMultipartFile(
                "file", "short.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03});

        assertThatThrownBy(() -> service.upload(conversationId, tooShort))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getReason())
                                .isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ."));
    }

    @Test
    void uploadThrowsBadGatewayWhenCloudinaryThrowsIOException() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("cloudinary down"));

        assertThatThrownBy(() -> service.upload(conversationId, file))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason())
                            .isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
    }

    @Test
    void uploadThrowsBadGatewayWhenCloudinaryThrowsRuntimeException() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service.upload(conversationId, file))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void uploadThrowsBadGatewayWhenPublicIdMissingFromResult() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> resultWithoutPublicId = new HashMap<>();
        resultWithoutPublicId.put("secure_url", "https://res.cloudinary.com/demo/image/upload/chat.jpg");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(resultWithoutPublicId);

        assertThatThrownBy(() -> service.upload(conversationId, file))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void uploadThrowsBadGatewayWhenPublicIdIsBlank() throws Exception {
        UUID conversationId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("public_id", "   "));

        assertThatThrownBy(() -> service.upload(conversationId, file))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void signUrlReturnsNullWhenPublicIdIsNull() {
        assertThat(service.signUrl(null)).isNull();
        verify(cloudinary, never()).url();
    }

    @Test
    void signUrlReturnsNullWhenPublicIdIsBlank() {
        assertThat(service.signUrl("   ")).isNull();
        verify(cloudinary, never()).url();
    }

    @Test
    void signUrlGeneratesSignedAuthenticatedUrlForValidPublicId() {
        String publicId = "movehome/chat/conv-1/abc";
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));
        String expectedUrl = realCloudinary.url()
                .resourceType("image")
                .type("authenticated")
                .secure(true)
                .signed(true)
                .generate(publicId);
        when(cloudinary.url()).thenReturn(realCloudinary.url());

        String result = service.signUrl(publicId);

        assertThat(result).isEqualTo(expectedUrl);
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile(
                "file", "anh.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
    }

    private byte[] validPngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
        };
    }

    private byte[] validWebpBytes() {
        byte[] bytes = new byte[12];
        System.arraycopy("RIFF".getBytes(), 0, bytes, 0, 4);
        bytes[4] = 0;
        bytes[5] = 0;
        bytes[6] = 0;
        bytes[7] = 0;
        System.arraycopy("WEBP".getBytes(), 0, bytes, 8, 4);
        return bytes;
    }
}
