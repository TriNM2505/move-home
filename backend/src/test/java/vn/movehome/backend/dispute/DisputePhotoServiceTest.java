package vn.movehome.backend.dispute;

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
import java.time.OffsetDateTime;
import java.util.HashMap;
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

@ExtendWith(MockitoExtension.class)
class DisputePhotoServiceTest {

    @Mock
    private DisputePhotoRepository disputePhotoRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private DisputePhotoService service;

    @BeforeEach
    void setUp() {
        service = new DisputePhotoService(disputePhotoRepository, disputeRepository, cloudinary);
    }

    @Test
    void uploadStoresPhotoWhenOwnerAndUnderLimit() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        MockMultipartFile file = validJpeg();

        String publicId = "movehome/disputes/" + disputeId + "/photo1";

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/photo1.jpg",
                "public_id", publicId));

        service.upload(disputeId, customerId, file);

        ArgumentCaptor<DisputePhoto> captor = ArgumentCaptor.forClass(DisputePhoto.class);
        verify(disputePhotoRepository).save(captor.capture());
        DisputePhoto saved = captor.getValue();
        assertThat(saved.getDisputeId()).isEqualTo(disputeId);
        assertThat(saved.getUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/photo1.jpg");
        assertThat(saved.getPublicId()).isEqualTo(publicId);
        assertThat(saved.getUploadedByUserId()).isEqualTo(customerId);
    }

    @Test
    void uploadRejectsWhenDisputeNotFound() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(disputeId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("DISPUTE_NOT_FOUND|Không tìm thấy khiếu nại.");
                });

        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void uploadRejectsWhenCustomerIsNotDisputeOwner() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(UUID.randomUUID()).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.upload(disputeId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo("FORBIDDEN|Bạn không có quyền đính kèm ảnh cho khiếu nại này.");
                });

        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void uploadRejectsWhenAlreadyAtMaxPhotos() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(3L);

        assertThatThrownBy(() -> service.upload(disputeId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("TOO_MANY_PHOTOS|Mỗi khiếu nại chỉ được đính kèm tối đa 3 ảnh.");
                });

        verify(disputePhotoRepository, never()).save(any());
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadRejectsEmptyFile() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.upload(disputeId, customerId, emptyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
    }

    @Test
    void uploadRejectsOversizedFile() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        byte[] oversizedContent = new byte[(int) DisputePhotoService.MAX_FILE_SIZE + 1];
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversizedContent);

        assertThatThrownBy(() -> service.upload(disputeId, customerId, oversizedFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Kích thước ảnh không được vượt quá 1,5 MB.");
                });
    }

    @Test
    void uploadRejectsFileThatCannotBeRead() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        MultipartFile unreadableFile = mock(MultipartFile.class);
        when(unreadableFile.isEmpty()).thenReturn(false);
        when(unreadableFile.getSize()).thenReturn(10L);
        when(unreadableFile.getBytes()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.upload(disputeId, customerId, unreadableFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên.");
                });
    }

    @Test
    void uploadRejectsFileWhoseContentIsNotARealSupportedImage() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "không phải ảnh hợp lệ".getBytes());

        assertThatThrownBy(() -> service.upload(disputeId, customerId, fakeImage))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });

        verify(cloudinary, never()).uploader();
        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void uploadRejectsFileShorterThanPngSignatureLength() {
        // Byte array qua ngan (< 8 byte) de kich hoat nhanh "return false" som trong isPng()
        // (khac voi test tren dung chuoi dai hon 8 byte roi moi mismatch trong vong lap).
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        MockMultipartFile tooShort = new MockMultipartFile(
                "file", "short.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03});

        assertThatThrownBy(() -> service.upload(disputeId, customerId, tooShort))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });

        verify(cloudinary, never()).uploader();
        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void uploadAcceptsPngSignature() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/photo.png",
                "public_id", "movehome/disputes/photo-png"));
        byte[] pngSignature = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile pngFile = new MockMultipartFile("file", "photo.png", "image/png", pngSignature);

        service.upload(disputeId, customerId, pngFile);

        verify(disputePhotoRepository).save(any(DisputePhoto.class));
    }

    @Test
    void uploadAcceptsWebpSignature() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/photo.webp",
                "public_id", "movehome/disputes/photo-webp"));
        byte[] webpSignature = "RIFF0000WEBP".getBytes();
        MockMultipartFile webpFile = new MockMultipartFile("file", "photo.webp", "image/webp", webpSignature);

        service.upload(disputeId, customerId, webpFile);

        verify(disputePhotoRepository).save(any(DisputePhoto.class));
    }

    @Test
    void uploadFailsWithBadGatewayWhenCloudinaryReturnsInvalidSecureUrl() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "http://not-secure.com/photo.jpg",
                "public_id", "movehome/disputes/photo1"));

        assertThatThrownBy(() -> service.upload(disputeId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });

        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void uploadFailsWithBadGatewayWhenCloudinaryReturnsBlankPublicId() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> resultWithBlankPublicId = new HashMap<>();
        resultWithBlankPublicId.put("secure_url", "https://res.cloudinary.com/demo/image/upload/photo1.jpg");
        resultWithBlankPublicId.put("public_id", "   ");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(resultWithBlankPublicId);

        assertThatThrownBy(() -> service.upload(disputeId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });

        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void uploadFailsWithBadGatewayWhenCloudinaryThrows() throws Exception {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().id(disputeId).customerId(customerId).build();
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputePhotoRepository.countByDisputeId(disputeId)).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("cloudinary down"));

        assertThatThrownBy(() -> service.upload(disputeId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });

        verify(disputePhotoRepository, never()).save(any());
    }

    @Test
    void signedUrlsReturnsSignedUrlPerPhotoOrderedByUploadedAt() {
        UUID disputeId = UUID.randomUUID();
        DisputePhoto photo1 = DisputePhoto.builder()
                .id(UUID.randomUUID())
                .disputeId(disputeId)
                .url("https://res.cloudinary.com/demo/image/upload/photo1.jpg")
                .publicId("movehome/disputes/photo1")
                .uploadedByUserId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .build();
        DisputePhoto photo2 = DisputePhoto.builder()
                .id(UUID.randomUUID())
                .disputeId(disputeId)
                .url("https://res.cloudinary.com/demo/image/upload/photo2.jpg")
                .publicId("movehome/disputes/photo2")
                .uploadedByUserId(UUID.randomUUID())
                .uploadedAt(OffsetDateTime.now())
                .build();
        when(disputePhotoRepository.findByDisputeIdOrderByUploadedAtAsc(disputeId))
                .thenReturn(List.of(photo1, photo2));

        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));
        when(cloudinary.url()).thenAnswer(invocation -> realCloudinary.url());

        List<String> urls = service.signedUrls(disputeId);

        String expectedUrl1 = realCloudinary.url()
                .resourceType("image").type("authenticated").secure(true).signed(true)
                .generate("movehome/disputes/photo1");
        String expectedUrl2 = realCloudinary.url()
                .resourceType("image").type("authenticated").secure(true).signed(true)
                .generate("movehome/disputes/photo2");

        assertThat(urls).containsExactly(expectedUrl1, expectedUrl2);
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile(
                "file",
                "evidence.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
    }
}
