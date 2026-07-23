package vn.movehome.backend.order;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationPhotoServiceTest {

    @Mock
    private OrderCancellationPhotoRepository photoRepository;

    @Mock
    private OrderCancellationRefundRepository refundRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private OrderCancellationPhotoService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationPhotoService(photoRepository, refundRepository, cloudinary);
    }

    private OrderCancellationRefund pendingRefund(UUID orderId, UUID customerId) {
        return OrderCancellationRefund.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .customerId(customerId)
                .reason("Doi y")
                .status(OrderCancellationRefund.STATUS_PENDING)
                .build();
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile("file", "cancel.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
    }

    private MockMultipartFile validPng() {
        return new MockMultipartFile("file", "cancel.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02});
    }

    private MockMultipartFile validWebp() {
        byte[] content = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, content, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, content, 8, 4);
        return new MockMultipartFile("file", "cancel.webp", "image/webp", content);
    }

    // ===================== uploadByOrder =====================

    @Test
    void uploadByOrderThrowsNotFoundWhenNoRefundForOrderAndCustomer() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_NOT_FOUND|Không tìm thấy yêu cầu hủy đơn để đính kèm ảnh.");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrderThrowsConflictWhenRefundAlreadyProcessed() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        refund.setStatus(OrderCancellationRefund.STATUS_REFUNDED);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "CANCELLATION_ALREADY_PROCESSED|Yêu cầu hủy đơn đã được xử lý, không thể đính kèm ảnh.");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrderThrowsWhenAlreadyHasMaxPhotos() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "TOO_MANY_PHOTOS|Mỗi yêu cầu hủy chỉ được đính kèm tối đa 3 ảnh.");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrderThrowsWhenFileIsNull() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
    }

    @Test
    void uploadByOrderThrowsWhenFileIsEmpty() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        MultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, emptyFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp tải lên không được để trống.");
                });
    }

    @Test
    void uploadByOrderThrowsWhenFileExceedsMaxSize() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);

        MultipartFile tooBig = mock(MultipartFile.class);
        when(tooBig.isEmpty()).thenReturn(false);
        when(tooBig.getSize()).thenReturn(OrderCancellationPhotoService.MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, tooBig))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_FILE|Kích thước ảnh không được vượt quá 1,5 MB.");
                });
    }

    @Test
    void uploadByOrderThrowsWhenFileCannotBeRead() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);

        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(10L);
        when(unreadable.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, unreadable))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên.");
                });
    }

    @Test
    void uploadByOrderThrowsWhenContentIsNotARealSupportedImage() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "khong phai anh".getBytes());

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, fakeImage))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrderThrowsWhenContentIsShorterThanPngSignatureLength() {
        // Byte array qua ngan (< 8 byte) de kich hoat nhanh "return false" som trong isPng()
        // (khac voi test tren dung chuoi dai hon 8 byte roi moi mismatch trong vong lap).
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        MockMultipartFile tooShort = new MockMultipartFile(
                "file", "short.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03});

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, tooShort))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadByOrderStoresPhotoForValidJpeg() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(1L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/cancel.jpg",
                "public_id", "movehome/cancellations/" + refund.getId()));

        service.uploadByOrder(orderId, customerId, validJpeg());

        ArgumentCaptor<OrderCancellationPhoto> captor = ArgumentCaptor.forClass(OrderCancellationPhoto.class);
        verify(photoRepository).save(captor.capture());
        OrderCancellationPhoto saved = captor.getValue();
        assertThat(saved.getCancellationId()).isEqualTo(refund.getId());
        assertThat(saved.getUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/cancel.jpg");
        assertThat(saved.getPublicId()).isEqualTo("movehome/cancellations/" + refund.getId());
        assertThat(saved.getUploadedByUserId()).isEqualTo(customerId);
    }

    @Test
    void uploadByOrderStoresPhotoForValidPng() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/cancel.png",
                "public_id", "movehome/cancellations/" + refund.getId()));

        service.uploadByOrder(orderId, customerId, validPng());

        verify(photoRepository).save(any(OrderCancellationPhoto.class));
    }

    @Test
    void uploadByOrderStoresPhotoForValidWebp() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/cancel.webp",
                "public_id", "movehome/cancellations/" + refund.getId()));

        service.uploadByOrder(orderId, customerId, validWebp());

        verify(photoRepository).save(any(OrderCancellationPhoto.class));
    }

    // ===================== uploadToCloudinary error branches =====================

    @Test
    void uploadByOrderWrapsCloudinaryIOExceptionAsBadGateway() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo(
                            "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadByOrderWrapsCloudinaryRuntimeExceptionAsBadGateway() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo(
                            "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
    }

    @Test
    void uploadByOrderWrapsMissingSecureUrlAsBadGateway() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "http://not-secure.example.com/cancel.jpg",
                "public_id", "movehome/cancellations/abc"));

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo(
                            "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
    }

    @Test
    void uploadByOrderWrapsMissingPublicIdAsBadGateway() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderCancellationRefund refund = pendingRefund(orderId, customerId);
        when(refundRepository.findByOrderIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(refund));
        when(photoRepository.countByCancellationId(refund.getId())).thenReturn(0L);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> resultWithBlankPublicId = new java.util.HashMap<>();
        resultWithBlankPublicId.put("secure_url", "https://res.cloudinary.com/demo/image/upload/cancel.jpg");
        resultWithBlankPublicId.put("public_id", "  ");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(resultWithBlankPublicId);

        assertThatThrownBy(() -> service.uploadByOrder(orderId, customerId, validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo(
                            "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
    }

    // ===================== signedUrls =====================

    @Test
    void signedUrlsMapsEachPhotoToSignedCloudinaryUrl() {
        UUID cancellationId = UUID.randomUUID();
        OrderCancellationPhoto photo1 = OrderCancellationPhoto.builder()
                .id(UUID.randomUUID())
                .cancellationId(cancellationId)
                .url("https://res.cloudinary.com/demo/image/upload/1.jpg")
                .publicId("movehome/cancellations/1")
                .uploadedAt(OffsetDateTime.now())
                .build();
        OrderCancellationPhoto photo2 = OrderCancellationPhoto.builder()
                .id(UUID.randomUUID())
                .cancellationId(cancellationId)
                .url("https://res.cloudinary.com/demo/image/upload/2.jpg")
                .publicId("movehome/cancellations/2")
                .uploadedAt(OffsetDateTime.now())
                .build();
        when(photoRepository.findByCancellationIdOrderByUploadedAtAsc(cancellationId))
                .thenReturn(List.of(photo1, photo2));

        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "demo", "api_key", "key", "api_secret", "secret"));
        when(cloudinary.url()).thenAnswer(invocation -> realCloudinary.url());

        List<String> urls = service.signedUrls(cancellationId);

        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).isEqualTo(realCloudinary.url()
                .resourceType("image").type("authenticated").secure(true).signed(true)
                .generate("movehome/cancellations/1"));
        assertThat(urls.get(1)).isEqualTo(realCloudinary.url()
                .resourceType("image").type("authenticated").secure(true).signed(true)
                .generate("movehome/cancellations/2"));
    }

    @Test
    void signedUrlsReturnsEmptyListWhenNoPhotos() {
        UUID cancellationId = UUID.randomUUID();
        when(photoRepository.findByCancellationIdOrderByUploadedAtAsc(cancellationId))
                .thenReturn(List.of());

        List<String> urls = service.signedUrls(cancellationId);

        assertThat(urls).isEmpty();
    }

    private static MultipartFile mock(Class<MultipartFile> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
