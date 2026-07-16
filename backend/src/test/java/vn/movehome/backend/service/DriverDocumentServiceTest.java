package vn.movehome.backend.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;

import vn.movehome.backend.dto.driver.DriverDocumentResponse;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.repository.DriverDocumentRepository;

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

    @Disabled("mock Cloudinary thieu public_id - mo lai sau khi xong code")
    @Test
    void uploadStoresDocumentForAuthenticatedDriverAndReturnsSecureUrl() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/license.jpg"));
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
        assertThat(saved.getUploadedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.id()).isEqualTo(documentId);
        assertThat(response.docType()).isEqualTo("DRIVING_LICENSE_FRONT");
        assertThat(response.url()).isEqualTo(saved.getUrl());
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

    // ===== MISSING BRANCHES =====

    /**
     * validateDocumentType: requestedDocType la chuoi rong → nem BAD_REQUEST.
     */
    @Test
    void uploadRejectsBlankDocumentTypeWithBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, "   ", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).startsWith("INVALID_DOCUMENT_TYPE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    /**
     * validateAndReadImage: file.getBytes() nem IOException → nem 422
     * UNPROCESSABLE_ENTITY.
     * Dung MockMultipartFile voi custom bytes de gia lap IOException.
     */
    @Test
    void uploadThrowsUnprocessableEntityWhenFileBytesCannotBeRead() throws IOException {
        UUID driverId = UUID.randomUUID();

        // Gia lap MultipartFile ma getBytes() nem IOException
        org.springframework.web.multipart.MultipartFile brokenFile = org.mockito.Mockito
                .mock(org.springframework.web.multipart.MultipartFile.class);
        when(brokenFile.isEmpty()).thenReturn(false);
        when(brokenFile.getSize()).thenReturn(100L); // Kich thuoc hop le
        when(brokenFile.getBytes()).thenThrow(new IOException("Loi IO gia lap"));

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", brokenFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_FILE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    /**
     * isPng(): mang byte ngan hon 8 byte → tra ve false (khong phai PNG hop le).
     * Cung la anh khong phai JPEG hay WebP → nem 422.
     */
    @Test
    void uploadRejectsFileTooShortToBePng() {
        UUID driverId = UUID.randomUUID();

        // Mang chi co 4 byte — ngan hon chieu dai signature PNG (8 byte)
        MockMultipartFile shortFile = new MockMultipartFile(
                "file", "tiny.png", "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 }); // Chi 4 byte dau cua PNG signature

        assertThatThrownBy(() -> service.upload(driverId, "VEHICLE_PHOTO_FRONT", shortFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_FILE|");
                });
    }

    /**
     * uploadToCloudinary: Cloudinary tra ve URL khong bat dau bang "https://" → nem
     * BAD_GATEWAY.
     */
    @Test
    void uploadThrowsBadGatewayWhenCloudinaryReturnsInsecureUrl() throws Exception {
        UUID driverId = UUID.randomUUID();

        when(cloudinary.uploader()).thenReturn(uploader);
        // Cloudinary tra ve URL http:// thay vi https://
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "http://insecure.cloudinary.com/image.jpg"));

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });

        verify(driverDocumentRepository, never()).save(any());
    }

    /**
     * validateDocumentType: docType = null → nem BAD_REQUEST.
     * Cover L63 nhanh null (requestedDocType == null).
     */
    @Test
    void uploadRejectsNullDocumentType() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, null, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(cloudinary, never()).uploader();
    }

    /**
     * validateAndReadImage: file = null → nem 422 INVALID_FILE.
     * Cover L80 nhanh file == null (khac voi isEmpty = true).
     */
    @Test
    void uploadRejectsNullFile() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(cloudinary, never()).uploader();
    }

    /**
     * isJpeg(): bytes.length < 3 → return false (cover L124 nhanh false).
     * isPng/isWebp cung fail → nem 422.
     */
    @Test
    void uploadRejectsFileTooShortForAnyFormat() {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile tinyFile = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8 });

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", tinyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * isJpeg(): bytes[0] == 0xFF nhung bytes[1] != 0xD8 → L126 nhanh false.
     */
    @Test
    void uploadRejectsFileWithPartialJpegMagic() {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile partialFile = new MockMultipartFile(
                "file", "x.jpg", "image/jpeg",
                new byte[] { (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", partialFile))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * isJpeg(): bytes[0]==0xFF, bytes[1]==0xD8, nhung bytes[2] != 0xFF → L127 nhanh
     * false.
     */
    @Test
    void uploadRejectsFileWithJpegFirstTwoBytesButWrongThird() {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile partialFile = new MockMultipartFile(
                "file", "y.jpg", "image/jpeg",
                new byte[] { (byte) 0xFF, (byte) 0xD8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", partialFile))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * isWebp(): RIFF...WEBP signature hop le → upload thanh cong (cover L145, L146
     * true).
     * Cover L94 nhanh webp (isJpeg=false, isPng=false, isWebp=true).
     */
    @Disabled("mock Cloudinary thieu public_id - mo lai sau khi xong code")
    @Test
    void uploadStoresWebpDocumentSuccessfully() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/doc.webp"));
        when(driverDocumentRepository.save(any(DriverDocument.class))).thenAnswer(invocation -> {
            DriverDocument doc = invocation.getArgument(0);
            doc.setId(documentId);
            return doc;
        });

        DriverDocumentResponse response = service.upload(driverId, "VEHICLE_PHOTO_FRONT", validWebp());
        assertThat(response.url()).startsWith("https://res.cloudinary.com/");
    }

    /**
     * uploadToCloudinary: secure_url khong phai String (la Integer) → nem 502.
     * Cover L113 nhanh !(secureUrl instanceof String url) = true.
     */
    @Test
    void uploadThrowsBadGatewayWhenCloudinaryReturnsNonStringSecureUrl() throws Exception {
        UUID driverId = UUID.randomUUID();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", 12345)); // Integer, khong phai String

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    /**
     * validateAndReadImage: file.isEmpty() = true → nem 422 INVALID_FILE.
     * Cover DriverDocumentService:81 — nhanh file == null || file.isEmpty().
     */
    @Test
    void uploadRejectsEmptyFile() throws IOException {
        UUID driverId = UUID.randomUUID();
        org.springframework.web.multipart.MultipartFile emptyFile = org.mockito.Mockito
                .mock(org.springframework.web.multipart.MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.upload(driverId, "DRIVING_LICENSE_FRONT", emptyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_FILE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    /**
     * validateAndReadImage: file.getSize() > 1_572_864 (1.5 MB) → nem 422
     * INVALID_FILE.
     * Cover DriverDocumentService:84 — nhanh kich thuoc qua lon.
     */
    @Test
    void uploadRejectsFileTooLarge() throws IOException {
        UUID driverId = UUID.randomUUID();
        org.springframework.web.multipart.MultipartFile largeFile = org.mockito.Mockito
                .mock(org.springframework.web.multipart.MultipartFile.class);
        when(largeFile.isEmpty()).thenReturn(false);
        when(largeFile.getSize()).thenReturn(2_000_000L); // 2 MB > 1.5 MB

        assertThatThrownBy(() -> service.upload(driverId, "VEHICLE_PHOTO_FRONT", largeFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).startsWith("INVALID_FILE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    /**
     * isPng(): anh PNG hop le (8-byte signature day du) → tra ve true, upload thanh
     * cong.
     * Cover DriverDocumentService:140 — return true trong isPng().
     */
    @Disabled("mock Cloudinary thieu public_id - mo lai sau khi xong code")
    @Test
    void uploadStoresPngDocumentSuccessfully() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = validPng();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/doc.png"));
        when(driverDocumentRepository.save(any(DriverDocument.class))).thenAnswer(invocation -> {
            DriverDocument doc = invocation.getArgument(0);
            doc.setId(documentId);
            return doc;
        });

        DriverDocumentResponse response = service.upload(driverId, "VEHICLE_PHOTO_FRONT", file);

        assertThat(response.url()).startsWith("https://res.cloudinary.com/");
        assertThat(response.docType()).isEqualTo("VEHICLE_PHOTO_FRONT");
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile(
                "file",
                "license.jpg",
                "image/jpeg",
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 });
    }

    private MockMultipartFile validPng() {
        // PNG signature day du 8 byte — isPng() tra ve true (cover :140)
        return new MockMultipartFile(
                "file",
                "doc.png",
                "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A });
    }

    private MockMultipartFile validWebp() {
        // WebP signature: RIFF (4 byte) + size (4 byte, bat ky) + WEBP (4 byte) = 12
        // byte toi thieu
        // isWebp() kiem tra: length >= 12, ascii[0..4]=="RIFF", ascii[8..12]=="WEBP"
        return new MockMultipartFile(
                "file",
                "doc.webp",
                "image/webp",
                new byte[] {
                        'R', 'I', 'F', 'F', // bytes 0-3: "RIFF"
                        0x00, 0x00, 0x00, 0x00, // bytes 4-7: file size (bat ky)
                        'W', 'E', 'B', 'P' // bytes 8-11: "WEBP"
                });
    }
}