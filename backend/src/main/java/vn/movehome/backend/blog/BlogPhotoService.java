package vn.movehome.backend.blog;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anh bai dang cong dong — upload Cloudinary signed server-side (AC-10).
 * Khac dispute/damage: day la noi dung CONG KHAI nen delivery type=upload (public URL),
 * KHONG dung "authenticated" (khong can signed URL het han). Van ky upload server-side
 * de khong lo API key phia client (AC-10).
 * Validate magic number JPEG/PNG/WebP + chan cung 1.5MB/anh, toi da 3 anh/bai.
 * Pattern validate dung lai tu ChatImageService / DisputePhotoService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogPhotoService {

    static final int MAX_PHOTOS = 3;
    static final long MAX_FILE_SIZE = 1_572_864L; // 1.5MB

    private final BlogPostPhotoRepository photoRepository;
    private final Cloudinary cloudinary;

    /** Upload + luu tat ca anh cho 1 bai. Tra ve danh sach da luu (theo thu tu). */
    @Transactional
    public List<BlogPostPhoto> attachPhotos(UUID postId, UUID uploaderId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> valid = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();
        if (valid.isEmpty()) {
            return List.of();
        }
        if (valid.size() > MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TOO_MANY_PHOTOS|Mỗi bài đăng chỉ được đính kèm tối đa 3 ảnh.");
        }

        List<BlogPostPhoto> saved = new ArrayList<>();
        for (MultipartFile file : valid) {
            byte[] content = validateAndReadImage(file);
            CloudinaryUploadResult result = uploadToCloudinary(postId, content);
            saved.add(photoRepository.save(BlogPostPhoto.builder()
                    .postId(postId)
                    .url(result.secureUrl())
                    .publicId(result.publicId())
                    .uploadedByUserId(uploaderId)
                    .build()));
        }
        return saved;
    }

    /**
     * Xoa toan bo anh cua 1 bai (khi Manager xoa bai — Pha C). Destroy asset tren Cloudinary
     * de tranh rac free tier (AC-10 cleanup); loi destroy khong lam fail viec xoa bai (chi log).
     */
    @Transactional
    public void deletePhotosByPost(UUID postId) {
        List<BlogPostPhoto> photos = photoRepository.findByPostIdInOrderByUploadedAtAsc(List.of(postId));
        for (BlogPostPhoto photo : photos) {
            try {
                cloudinary.uploader().destroy(photo.getPublicId(), ObjectUtils.emptyMap());
            } catch (IOException | RuntimeException ex) {
                log.warn("Không xóa được ảnh blog trên Cloudinary (public_id={}): {}",
                        photo.getPublicId(), ex.getMessage());
            }
            photoRepository.delete(photo);
        }
    }

    private byte[] validateAndReadImage(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalidFile("Kích thước ảnh không được vượt quá 1,5 MB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw invalidFile("Không thể đọc tệp tải lên.");
        }
        if (!isJpeg(content) && !isPng(content) && !isWebp(content)) {
            throw invalidFile("Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ.");
        }
        return content;
    }

    private ResponseStatusException invalidFile(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILE|" + message);
    }

    private CloudinaryUploadResult uploadToCloudinary(UUID postId, byte[] content) {
        String folder = "movehome/blog/%s".formatted(postId);
        try {
            // type=upload → delivery cong khai (noi dung blog cong dong)
            Map<?, ?> uploadResult = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", folder,
                    "type", "upload"));
            Object secureUrl = uploadResult.get("secure_url");
            Object publicId = uploadResult.get("public_id");
            if (!(secureUrl instanceof String url) || !url.startsWith("https://")) {
                throw new IOException("Cloudinary không trả về secure_url hợp lệ");
            }
            if (!(publicId instanceof String pid) || pid.isBlank()) {
                throw new IOException("Cloudinary không trả về public_id hợp lệ");
            }
            return new CloudinaryUploadResult(url, pid);
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "CLOUDINARY_UNAVAILABLE|Không thể tải ảnh lên. Vui lòng thử lại.", ex);
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
    }

    private record CloudinaryUploadResult(String secureUrl, String publicId) {
    }
}
