package vn.movehome.backend.dispute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;

/**
 * Ảnh bằng chứng khiếu nại — upload Cloudinary signed server-side (AC-10).
 * Resource type "authenticated" → URL hiển thị phải có chữ ký (signed URL) mới xem được;
 * bảo vệ ảnh đồ đạc/riêng tư của khách, khác avatar (public delivery).
 * Tối đa 3 ảnh/khiếu nại; validate magic number JPEG/PNG/WebP + chặn cứng 1.5MB.
 */
@Service
@RequiredArgsConstructor
public class DisputePhotoService {

    static final int MAX_PHOTOS = 3;
    static final long MAX_FILE_SIZE = 1_572_864L; // 1.5MB

    private final DisputePhotoRepository disputePhotoRepository;
    private final DisputeRepository disputeRepository;
    private final Cloudinary cloudinary;

    /** Khách (chủ khiếu nại) upload 1 ảnh bằng chứng. Tối đa 3 ảnh/khiếu nại. */
    @Transactional
    public void upload(UUID disputeId, UUID customerId, MultipartFile file) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND|Không tìm thấy khiếu nại."));
        if (!dispute.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN|Bạn không có quyền đính kèm ảnh cho khiếu nại này.");
        }
        if (disputePhotoRepository.countByDisputeId(disputeId) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TOO_MANY_PHOTOS|Mỗi khiếu nại chỉ được đính kèm tối đa 3 ảnh.");
        }

        byte[] content = validateAndReadImage(file);
        CloudinaryUploadResult result = uploadToCloudinary(disputeId, content);

        disputePhotoRepository.save(DisputePhoto.builder()
                .disputeId(disputeId)
                .url(result.secureUrl())
                .publicId(result.publicId())
                .uploadedByUserId(customerId)
                .build());
    }

    /** Signed URL cho từng ảnh của 1 khiếu nại — dùng cho Manager xem (detail). */
    @Transactional(readOnly = true)
    public List<String> signedUrls(UUID disputeId) {
        return disputePhotoRepository.findByDisputeIdOrderByUploadedAtAsc(disputeId)
                .stream()
                .map(photo -> signUrl(photo.getPublicId()))
                .toList();
    }

    private byte[] validateAndReadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile("Tệp tải lên không được để trống.");
        }
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

    private CloudinaryUploadResult uploadToCloudinary(UUID disputeId, byte[] content) {
        String folder = "movehome/disputes/%s".formatted(disputeId);
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", folder,
                    "type", "authenticated"));
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

    // Signed URL cho resource authenticated — vòng đời bảo mật dựa JWT 15 phút (như DriverDocument, AC-10).
    private String signUrl(String publicId) {
        return cloudinary.url()
                .resourceType("image")
                .type("authenticated")
                .secure(true)
                .signed(true)
                .generate(publicId);
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
