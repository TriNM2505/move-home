package vn.movehome.backend.blog;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test BlogPhotoService — upload/validate anh Cloudinary + xoa anh khi go bai (Pha C).
 */
@ExtendWith(MockitoExtension.class)
class BlogPhotoServiceTest {

    @Mock
    private BlogPostPhotoRepository photoRepository;
    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;

    private BlogPhotoService service;

    @BeforeEach
    void setUp() {
        service = new BlogPhotoService(photoRepository, cloudinary);
    }

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};
    private static final byte[] WEBP_BYTES;

    static {
        byte[] bytes = new byte[16];
        byte[] riff = "RIFF".getBytes();
        byte[] webp = "WEBP".getBytes();
        System.arraycopy(riff, 0, bytes, 0, 4);
        System.arraycopy(webp, 0, bytes, 8, 4);
        WEBP_BYTES = bytes;
    }

    private MockMultipartFile jpegFile(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", JPEG_BYTES);
    }

    // ---------- attachPhotos: cac nhanh dau vao rong ----------

    @Test
    void attachPhotos_filesNull_traVeDanhSachRong() {
        List<BlogPostPhoto> result = service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), null);

        assertThat(result).isEmpty();
        verify(photoRepository, never()).save(any());
    }

    @Test
    void attachPhotos_filesRong_traVeDanhSachRong() {
        List<BlogPostPhoto> result = service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void attachPhotos_tatCaFileRongHoacNull_traVeDanhSachRong() {
        List<MultipartFile> files = new ArrayList<>();
        files.add(null);
        files.add(new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]));

        List<BlogPostPhoto> result = service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), files);

        assertThat(result).isEmpty();
        verify(photoRepository, never()).save(any());
    }

    @Test
    void attachPhotos_vuotQua3Anh_tra422() {
        List<MultipartFile> files = List.of(jpegFile("1.jpg"), jpegFile("2.jpg"), jpegFile("3.jpg"), jpegFile("4.jpg"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), files))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("TOO_MANY_PHOTOS|Mỗi bài đăng chỉ được đính kèm tối đa 3 ảnh.");
                });

        verify(photoRepository, never()).save(any());
    }

    // ---------- attachPhotos: happy path + validate loai anh ----------

    @Test
    void attachPhotos_jpegHopLe_uploadVaLuuTheoThuTu() throws IOException {
        UUID postId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/blog1.jpg",
                "public_id", "movehome/blog/pid1"));
        when(photoRepository.save(any(BlogPostPhoto.class))).thenAnswer(inv -> {
            BlogPostPhoto p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        List<BlogPostPhoto> result = service.attachPhotos(postId, uploaderId, List.of(jpegFile("a.jpg")));

        assertThat(result).hasSize(1);
        ArgumentCaptor<BlogPostPhoto> captor = ArgumentCaptor.forClass(BlogPostPhoto.class);
        verify(photoRepository).save(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(postId);
        assertThat(captor.getValue().getUploadedByUserId()).isEqualTo(uploaderId);
        assertThat(captor.getValue().getUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/blog1.jpg");
        assertThat(captor.getValue().getPublicId()).isEqualTo("movehome/blog/pid1");
    }

    @Test
    void attachPhotos_pngHopLe_uploadThanhCong() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/blog.png",
                "public_id", "pid-png"));
        when(photoRepository.save(any(BlogPostPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

        List<BlogPostPhoto> result = service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new MockMultipartFile("files", "a.png", "image/png", PNG_BYTES)));

        assertThat(result).hasSize(1);
    }

    @Test
    void attachPhotos_webpHopLe_uploadThanhCong() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/blog.webp",
                "public_id", "pid-webp"));
        when(photoRepository.save(any(BlogPostPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

        List<BlogPostPhoto> result = service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new MockMultipartFile("files", "a.webp", "image/webp", WEBP_BYTES)));

        assertThat(result).hasSize(1);
    }

    @Test
    void attachPhotos_khongPhaiAnhHopLe_tra422() {
        MockMultipartFile fakeFile = new MockMultipartFile(
                "files", "fake.jpg", "image/jpeg", "không phải ảnh".getBytes());

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(fakeFile)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });

        verify(photoRepository, never()).save(any());
    }

    @Test
    void attachPhotos_fileNganHonChuKyPng_tra422() {
        // Byte array qua ngan (< 8 byte) de kich hoat nhanh "return false" som trong isPng()
        // (khac voi test tren dung chuoi dai hon 8 byte roi moi mismatch trong vong lap).
        MockMultipartFile tooShort = new MockMultipartFile(
                "files", "short.jpg", "image/jpeg", new byte[]{0x01, 0x02, 0x03});

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(tooShort)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
                });

        verify(photoRepository, never()).save(any());
    }

    @Test
    void attachPhotos_fileQuaKichThuoc_tra422() {
        byte[] big = new byte[(int) BlogPhotoService.MAX_FILE_SIZE + 10];
        System.arraycopy(JPEG_BYTES, 0, big, 0, JPEG_BYTES.length);
        MockMultipartFile bigFile = new MockMultipartFile("files", "big.jpg", "image/jpeg", big);

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(bigFile)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Kích thước ảnh không được vượt quá 1,5 MB.");
                });
    }

    @Test
    void attachPhotos_khongDocDuocFile_tra422() throws IOException {
        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getSize()).thenReturn(10L);
        when(unreadable.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(unreadable)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_FILE|Không thể đọc tệp tải lên.");
                });
    }

    // ---------- attachPhotos: loi Cloudinary ----------

    @Test
    void attachPhotos_cloudinaryTraSecureUrlKhongHopLe_tra502() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "http://khong-phai-https.com/x.jpg",
                "public_id", "pid"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(jpegFile("a.jpg"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo("CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.");
                });
    }

    @Test
    void attachPhotos_cloudinaryTraPublicIdRong_tra502() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/x.jpg",
                "public_id", ""));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(jpegFile("a.jpg"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void attachPhotos_cloudinaryKhongTraSecureUrl_tra502() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("public_id", "pid"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(jpegFile("a.jpg"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void attachPhotos_cloudinaryKhongTraPublicId_tra502() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/x.jpg"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(jpegFile("a.jpg"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void attachPhotos_uploaderThrowsIOException_tra502() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(jpegFile("a.jpg"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void attachPhotos_uploaderThrowsRuntimeException_tra502() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.attachPhotos(UUID.randomUUID(), UUID.randomUUID(), List.of(jpegFile("a.jpg"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    // ---------- deletePhotosByPost ----------

    @Test
    void deletePhotosByPost_khongCoAnh_khongLamGi() {
        UUID postId = UUID.randomUUID();
        when(photoRepository.findByPostIdInOrderByUploadedAtAsc(List.of(postId))).thenReturn(List.of());

        service.deletePhotosByPost(postId);

        verify(photoRepository, never()).delete(any());
    }

    @Test
    void deletePhotosByPost_xoaThanhCongTrenCloudinaryVaDb() throws IOException {
        UUID postId = UUID.randomUUID();
        BlogPostPhoto photo = BlogPostPhoto.builder()
                .id(UUID.randomUUID()).postId(postId).url("https://cdn/1.jpg").publicId("pid-1").build();
        when(photoRepository.findByPostIdInOrderByUploadedAtAsc(List.of(postId))).thenReturn(List.of(photo));
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

        service.deletePhotosByPost(postId);

        verify(uploader).destroy("pid-1", Map.of());
        verify(photoRepository).delete(photo);
    }

    @Test
    void deletePhotosByPost_destroyThrowsIOException_vanXoaDbRecord() throws IOException {
        UUID postId = UUID.randomUUID();
        BlogPostPhoto photo = BlogPostPhoto.builder()
                .id(UUID.randomUUID()).postId(postId).url("https://cdn/1.jpg").publicId("pid-err").build();
        when(photoRepository.findByPostIdInOrderByUploadedAtAsc(List.of(postId))).thenReturn(List.of(photo));
        when(cloudinary.uploader()).thenReturn(uploader);
        doThrow(new IOException("cloudinary down")).when(uploader).destroy(anyString(), anyMap());

        service.deletePhotosByPost(postId);

        verify(photoRepository).delete(photo);
    }

    @Test
    void deletePhotosByPost_destroyThrowsRuntimeException_vanXoaDbRecord() throws IOException {
        UUID postId = UUID.randomUUID();
        BlogPostPhoto photo = BlogPostPhoto.builder()
                .id(UUID.randomUUID()).postId(postId).url("https://cdn/1.jpg").publicId("pid-rte").build();
        when(photoRepository.findByPostIdInOrderByUploadedAtAsc(List.of(postId))).thenReturn(List.of(photo));
        when(cloudinary.uploader()).thenReturn(uploader);
        doThrow(new RuntimeException("boom")).when(uploader).destroy(anyString(), anyMap());

        service.deletePhotosByPost(postId);

        verify(photoRepository).delete(photo);
    }

    @Test
    void deletePhotosByPost_nhieuAnh_xoaTatCa() throws IOException {
        UUID postId = UUID.randomUUID();
        BlogPostPhoto p1 = BlogPostPhoto.builder().id(UUID.randomUUID()).postId(postId)
                .url("https://cdn/1.jpg").publicId("pid-1").build();
        BlogPostPhoto p2 = BlogPostPhoto.builder().id(UUID.randomUUID()).postId(postId)
                .url("https://cdn/2.jpg").publicId("pid-2").build();
        when(photoRepository.findByPostIdInOrderByUploadedAtAsc(List.of(postId))).thenReturn(List.of(p1, p2));
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of());

        service.deletePhotosByPost(postId);

        verify(uploader, times(2)).destroy(anyString(), anyMap());
        verify(photoRepository).delete(p1);
        verify(photoRepository).delete(p2);
    }
}
