package vn.movehome.backend.incident;

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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra DriverIncidentPhotoService (V44): upload anh bang chung su co qua Cloudinary signed upload (AC-10).
 * Pattern tham chieu: DriverDocumentServiceTest (cung logic validate + upload Cloudinary).
 */
@ExtendWith(MockitoExtension.class)
class DriverIncidentPhotoServiceTest {

    @Mock
    private DriverIncidentPhotoRepository photoRepository;

    @Mock
    private DriverIncidentReportRepository reportRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private DriverIncidentPhotoService service;

    private final UUID orderId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID incidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DriverIncidentPhotoService(photoRepository, reportRepository, cloudinary);
    }

    private DriverIncidentReport pendingReport() {
        return DriverIncidentReport.builder()
                .id(incidentId).orderId(orderId).driverId(driverId)
                .reason("Hong xe").orderStatusSnapshot("IN_PROGRESS")
                .status(DriverIncidentReport.STATUS_PENDING)
                .build();
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile(
                "file", "evidence.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
    }

    // ===================== uploadByOrder =====================

    @Test
    void uploadByOrder_success_savesPhotoLinkedToIncident() throws Exception {
        DriverIncidentReport report = pendingReport();
        String publicId = "movehome/incidents/" + incidentId;
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(report));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/evidence.jpg",
                "public_id", publicId));

        service.uploadByOrder(orderId, driverId, validJpeg());

        ArgumentCaptor<DriverIncidentPhoto> captor = ArgumentCaptor.forClass(DriverIncidentPhoto.class);
        verify(photoRepository).save(captor.capture());
        DriverIncidentPhoto saved = captor.getValue();
        assertThat(saved.getIncidentId()).isEqualTo(incidentId);
        assertThat(saved.getUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/evidence.jpg");
        assertThat(saved.getPublicId()).isEqualTo(publicId);
        assertThat(saved.getUploadedByUserId()).isEqualTo(driverId);
    }

    @Test
    void uploadByOrder_whenNoPendingIncidentForOrder_throwsNotFound() {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(
                            "INCIDENT_NOT_FOUND|Không tìm thấy báo cáo sự cố đang chờ để đính kèm ảnh.");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrder_whenIncidentBelongsToDifferentDriver_throwsNotFound() {
        DriverIncidentReport report = pendingReport();
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(report));

        UUID otherDriver = UUID.randomUUID();
        assertThatThrownBy(() -> service.uploadByOrder(orderId, otherDriver, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrder_whenAlreadyHasMaxPhotos_throwsUnprocessableEntity() {
        DriverIncidentReport report = pendingReport();
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(report));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(3L);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOO_MANY_PHOTOS|Mỗi báo cáo sự cố chỉ được đính kèm tối đa 3 ảnh.");
                });
        verify(photoRepository, never()).save(any());
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_rejectsNullFile() {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_rejectsEmptyFile() {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, emptyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống."));
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_rejectsFileExceedingMaxSize() {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        MultipartFile oversizedFile = mock(MultipartFile.class);
        when(oversizedFile.isEmpty()).thenReturn(false);
        when(oversizedFile.getSize()).thenReturn(DriverIncidentPhotoService.MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, oversizedFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo(
                                "INVALID_FILE|Kích thước ảnh không được vượt quá 1,5 MB."));
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_rejectsWhenReadingFileBytesFails() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        MultipartFile brokenFile = mock(MultipartFile.class);
        when(brokenFile.isEmpty()).thenReturn(false);
        when(brokenFile.getSize()).thenReturn(10L);
        when(brokenFile.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, brokenFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên."));
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_rejectsUnsupportedFileContent() {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "không phải ảnh".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, fakeFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_rejectsFileShorterThanPngSignatureLength() {
        // Byte array qua ngan (< 8 byte) de kich hoat nhanh "return false" som trong isPng()
        // (khac voi test tren dung chuoi dai hon 8 byte roi moi mismatch trong vong lap).
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        MockMultipartFile tooShort = new MockMultipartFile(
                "file", "short.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03});

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, tooShort))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrder_acceptsValidPngImage() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        MockMultipartFile pngFile = new MockMultipartFile(
                "file", "evidence.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/evidence.png",
                "public_id", "movehome/incidents/x"));

        service.uploadByOrder(orderId, driverId, pngFile);

        verify(photoRepository).save(any(DriverIncidentPhoto.class));
    }

    @Test
    void uploadByOrder_acceptsValidWebpImage() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        byte[] webpBytes = "RIFF????WEBP".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile webpFile = new MockMultipartFile("file", "evidence.webp", "image/webp", webpBytes);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/evidence.webp",
                "public_id", "movehome/incidents/y"));

        service.uploadByOrder(orderId, driverId, webpFile);

        verify(photoRepository).save(any(DriverIncidentPhoto.class));
    }

    @Test
    void uploadByOrder_throwsBadGatewayWhenCloudinaryReturnsInvalidSecureUrl() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "http://not-secure.example.com/x.jpg",
                "public_id", "movehome/incidents/x"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrder_throwsBadGatewayWhenCloudinaryReturnsBlankPublicId() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/x.jpg",
                "public_id", ""));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrder_throwsBadGatewayWhenCloudinaryUploadThrowsRuntimeException() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrder_throwsBadGatewayWhenCloudinaryUploadThrowsIOException() throws Exception {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_PENDING))
                .thenReturn(Optional.of(pendingReport()));
        when(photoRepository.countByIncidentId(incidentId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("timeout"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, driverId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).startsWith("CLOUDINARY_UNAVAILABLE|");
                });
        verify(photoRepository, never()).save(any());
    }

    // ===================== signedUrls =====================

    @Test
    void signedUrls_returnsSignedUrlPerPhotoOrderedByUploadedAt() {
        String publicId1 = "movehome/incidents/photo1";
        String publicId2 = "movehome/incidents/photo2";
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));
        String expectedUrl1 = realCloudinary.url()
                .resourceType("image").type("authenticated").secure(true).signed(true).generate(publicId1);
        String expectedUrl2 = realCloudinary.url()
                .resourceType("image").type("authenticated").secure(true).signed(true).generate(publicId2);

        DriverIncidentPhoto photo1 = DriverIncidentPhoto.builder()
                .id(UUID.randomUUID()).incidentId(incidentId).url("https://res.cloudinary.com/demo/1.jpg")
                .publicId(publicId1).uploadedByUserId(driverId)
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5))
                .build();
        DriverIncidentPhoto photo2 = DriverIncidentPhoto.builder()
                .id(UUID.randomUUID()).incidentId(incidentId).url("https://res.cloudinary.com/demo/2.jpg")
                .publicId(publicId2).uploadedByUserId(driverId)
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(photoRepository.findByIncidentIdOrderByUploadedAtAsc(incidentId))
                .thenReturn(List.of(photo1, photo2));
        when(cloudinary.url()).thenAnswer(invocation -> realCloudinary.url());

        List<String> result = service.signedUrls(incidentId);

        assertThat(result).containsExactly(expectedUrl1, expectedUrl2);
    }

    @Test
    void signedUrls_whenNoPhotos_returnsEmptyList() {
        when(photoRepository.findByIncidentIdOrderByUploadedAtAsc(incidentId)).thenReturn(List.of());

        List<String> result = service.signedUrls(incidentId);

        assertThat(result).isEmpty();
    }
}
