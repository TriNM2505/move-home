package vn.movehome.backend.service;

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
import vn.movehome.backend.dto.driver.DriverDocumentResponse;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.repository.DriverDocumentRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class DriverDocumentServiceTest {

    @Mock
    private DriverDocumentRepository driverDocumentRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private DriverDocumentService service;

    @BeforeEach
    void setUp() {
        service = new DriverDocumentService(driverDocumentRepository, cloudinary);
    }

    @Test
    void uploadStoresDocumentForAuthenticatedDriverAndReturnsSecureUrl() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();

        String publicId = "movehome/drivers/license";
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));
        String expectedSignedUrl = realCloudinary.url()
                .resourceType("image")
                .type("authenticated")
                .secure(true)
                .signed(true)
                .generate(publicId);

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of(
                        "secure_url", "https://res.cloudinary.com/demo/image/upload/license.jpg",
                        "public_id", publicId));
        when(cloudinary.url()).thenReturn(realCloudinary.url());
        when(driverDocumentRepository.save(any(DriverDocument.class))).thenAnswer(invocation -> {
            DriverDocument document = invocation.getArgument(0);
            document.setId(documentId);
            return document;
        });

        DriverDocumentResponse response = service.upload(driverId, " driving_license_front ", file);

        ArgumentCaptor<DriverDocument> documentCaptor = ArgumentCaptor.forClass(DriverDocument.class);
        verify(driverDocumentRepository).save(documentCaptor.capture());
        DriverDocument saved = documentCaptor.getValue();

        assertThat(saved.getDriverId()).isEqualTo(driverId);
        assertThat(saved.getDocType()).isEqualTo("DRIVING_LICENSE_FRONT");
        assertThat(saved.getUrl()).startsWith("https://res.cloudinary.com/");
        assertThat(saved.getPublicId()).isEqualTo(publicId);
        assertThat(saved.getUploadedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.id()).isEqualTo(documentId);
        assertThat(response.docType()).isEqualTo("DRIVING_LICENSE_FRONT");
        assertThat(response.url()).isEqualTo(expectedSignedUrl);
    }

    @Test
    void uploadRejectsUnsupportedDocumentTypeWithBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, "IDENTITY_CARD", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).startsWith("INVALID_DOCUMENT_TYPE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsBlankDocumentTypeWithBadRequest() {
        // Nhanh rieng: requestedDocType == null || isBlank() -> khac voi nhanh "khong nam trong allowlist".
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, "   ", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo(
                            "INVALID_DOCUMENT_TYPE|Loại tài liệu không hợp lệ. Chỉ chấp nhận giấy phép lái xe, "
                                    + "đăng ký xe hoặc ảnh xe.");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsNullDocumentTypeWithBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, null, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).startsWith("INVALID_DOCUMENT_TYPE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsFileWhoseContentIsNotARealSupportedImage() {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile renamedTextFile = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "không phải ảnh".getBytes());

        assertThatThrownBy(() -> service.upload(driverId, "VEHICLE_PHOTO_FRONT", renamedTextFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).startsWith("INVALID_FILE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsFileShorterThanPngSignatureLength() {
        // Byte array qua ngan (< 8 byte) de kich hoat nhanh "return false" som trong isPng()
        // (khac voi test tren dung chuoi dai hon 8 byte roi moi mismatch trong vong lap).
        UUID driverId = UUID.randomUUID();
        MockMultipartFile tooShort = new MockMultipartFile(
                "file", "short.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03});

        assertThatThrownBy(() -> service.upload(driverId, "VEHICLE_PHOTO_FRONT", tooShort))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).startsWith("INVALID_FILE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void getMyDocumentsQueriesOnlyAuthenticatedDriverId() {
        UUID driverId = UUID.randomUUID();
        OffsetDateTime uploadedAt = OffsetDateTime.now(ZoneOffset.UTC);
        DriverDocument document = DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .docType("VEHICLE_REGISTRATION")
                .url("https://res.cloudinary.com/demo/image/upload/registration.png")
                .uploadedAt(uploadedAt)
                .build();
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId))
                .thenReturn(List.of(document));

        List<DriverDocumentResponse> result = service.getMyDocuments(driverId);

        assertThat(result).containsExactly(new DriverDocumentResponse(
                document.getId(), document.getDocType(), document.getUrl(), uploadedAt));
        verify(driverDocumentRepository).findByDriverIdOrderByUploadedAtDesc(driverId);
    }

    @Test
    void uploadRejectsNullFile() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsEmptyFile() {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", emptyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadRejectsFileExceedingMaxSize() throws Exception {
        UUID driverId = UUID.randomUUID();
        MultipartFile oversizedFile = mock(MultipartFile.class);
        when(oversizedFile.isEmpty()).thenReturn(false);
        when(oversizedFile.getSize()).thenReturn(DriverDocumentService.MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", oversizedFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason())
                            .isEqualTo("INVALID_FILE|Kích thước tệp không được vượt quá 1,5 MB.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadRejectsWhenReadingFileBytesFails() throws Exception {
        UUID driverId = UUID.randomUUID();
        MultipartFile brokenFile = mock(MultipartFile.class);
        when(brokenFile.isEmpty()).thenReturn(false);
        when(brokenFile.getSize()).thenReturn(10L);
        when(brokenFile.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", brokenFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadAcceptsValidPngImage() throws Exception {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile pngFile = new MockMultipartFile(
                "file",
                "vehicle.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/vehicle.png",
                "public_id", "movehome/drivers/vehicle"));
        when(cloudinary.url()).thenReturn(realCloudinary.url());
        when(driverDocumentRepository.save(any(DriverDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DriverDocumentResponse response = service.upload(driverId, "VEHICLE_PHOTO_SIDE", pngFile);

        assertThat(response.docType()).isEqualTo("VEHICLE_PHOTO_SIDE");
    }

    @Test
    void uploadAcceptsValidWebpImage() throws Exception {
        UUID driverId = UUID.randomUUID();
        byte[] webpBytes = "RIFF????WEBP".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile webpFile = new MockMultipartFile("file", "vehicle.webp", "image/webp", webpBytes);
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/vehicle.webp",
                "public_id", "movehome/drivers/vehicle-webp"));
        when(cloudinary.url()).thenReturn(realCloudinary.url());
        when(driverDocumentRepository.save(any(DriverDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DriverDocumentResponse response = service.upload(driverId, "VEHICLE_PHOTO_REAR", webpFile);

        assertThat(response.docType()).isEqualTo("VEHICLE_PHOTO_REAR");
    }

    @Test
    void uploadThrowsBadGatewayWhenCloudinaryReturnsInvalidSecureUrl() throws Exception {
        UUID driverId = UUID.randomUUID();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "http://not-secure.example.com/image.jpg",
                "public_id", "movehome/drivers/x"));

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadThrowsBadGatewayWhenCloudinaryReturnsBlankPublicId() throws Exception {
        UUID driverId = UUID.randomUUID();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/x.jpg",
                "public_id", ""));

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadThrowsBadGatewayWhenCloudinaryUploadThrowsRuntimeException() throws Exception {
        UUID driverId = UUID.randomUUID();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
    }

    @Test
    void uploadThrowsBadGatewayWhenCloudinaryUploadThrowsIOException() throws Exception {
        UUID driverId = UUID.randomUUID();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("timeout"));

        assertThatThrownBy(() -> service.upload(driverId, "FACE_PHOTO", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
    }

    @Test
    void latestSignedUrlsByTypeReturnsFirstMatchPerTypeAndFiltersRequestedTypes() {
        UUID driverId = UUID.randomUUID();
        String publicId = "movehome/drivers/face";
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));
        String expectedSignedUrl = realCloudinary.url()
                .resourceType("image")
                .type("authenticated")
                .secure(true)
                .signed(true)
                .generate(publicId);

        DriverDocument newestFacePhoto = DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .docType("FACE_PHOTO")
                .publicId(publicId)
                .url("https://res.cloudinary.com/demo/image/upload/face-new.jpg")
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        DriverDocument olderFacePhoto = DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .docType("FACE_PHOTO")
                .url("https://res.cloudinary.com/demo/image/upload/face-old.jpg")
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .build();
        DriverDocument vehiclePhoto = DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .docType("VEHICLE_PHOTO_FRONT")
                .url("https://res.cloudinary.com/demo/image/upload/vehicle.jpg")
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        DriverDocument notRequested = DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .docType("DRIVING_LICENSE_FRONT")
                .url("https://res.cloudinary.com/demo/image/upload/license.jpg")
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId))
                .thenReturn(List.of(newestFacePhoto, olderFacePhoto, vehiclePhoto, notRequested));
        when(cloudinary.url()).thenReturn(realCloudinary.url());

        Map<String, String> result = service.latestSignedUrlsByType(
                driverId, Set.of("FACE_PHOTO", "VEHICLE_PHOTO_FRONT"));

        assertThat(result).hasSize(2);
        assertThat(result.get("FACE_PHOTO")).isEqualTo(expectedSignedUrl);
        assertThat(result.get("VEHICLE_PHOTO_FRONT")).isEqualTo(vehiclePhoto.getUrl());
        assertThat(result).doesNotContainKey("DRIVING_LICENSE_FRONT");
    }

    @Test
    void latestSignedUrlsByTypeReturnsEmptyMapWhenNoDocuments() {
        UUID driverId = UUID.randomUUID();
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)).thenReturn(List.of());

        Map<String, String> result = service.latestSignedUrlsByType(driverId, Set.of("FACE_PHOTO"));

        assertThat(result).isEmpty();
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile(
                "file",
                "license.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
    }
}
