package vn.movehome.backend.order;

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
 * Anh bang chung khach dinh kem khi huy don — Cloudinary signed upload server-side (AC-10).
 * Mirror DisputePhotoService: resource_type "authenticated" (xem qua signed URL), toi da 3 anh,
 * validate magic number JPEG/PNG/WebP + chan cung 1.5MB.
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationPhotoService {

    static final int MAX_PHOTOS = 3;
    static final long MAX_FILE_SIZE = 1_572_864L; // 1.5MB

    private final OrderCancellationPhotoRepository photoRepository;
    private final OrderCancellationRefundRepository refundRepository;
    private final Cloudinary cloudinary;

    /**
     * Khach (chu yeu cau) dinh kem 1 anh cho yeu cau hoan coc cua don minh.
     * HR-10: chi chu yeu cau moi upload duoc. Chi cho phep khi yeu cau con PENDING.
     */
    @Transactional
    public void uploadByOrder(UUID orderId, UUID customerId, MultipartFile file) {
        OrderCancellationRefund refund = refundRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CANCELLATION_NOT_FOUND|Không tìm thấy yêu cầu hủy đơn để đính kèm ảnh."));
        if (!OrderCancellationRefund.STATUS_PENDING.equals(refund.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CANCELLATION_ALREADY_PROCESSED|Yêu cầu hủy đơn đã được xử lý, không thể đính kèm ảnh.");
        }
        if (photoRepository.countByCancellationId(refund.getId()) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TOO_MANY_PHOTOS|Mỗi yêu cầu hủy chỉ được đính kèm tối đa 3 ảnh.");
        }

        byte[] content = validateAndReadImage(file);
        CloudinaryUploadResult result = uploadToCloudinary(refund.getId(), content);

        photoRepository.save(OrderCancellationPhoto.builder()
                .cancellationId(refund.getId())
                .url(result.secureUrl())
                .publicId(result.publicId())
                .uploadedByUserId(customerId)
                .build());
    }

    /** Signed URL cho tung anh cua 1 yeu cau — dung cho Manager xem (detail). */
    @Transactional(readOnly = true)
    public List<String> signedUrls(UUID cancellationId) {
        return photoRepository.findByCancellationIdOrderByUploadedAtAsc(cancellationId)
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

    private CloudinaryUploadResult uploadToCloudinary(UUID cancellationId, byte[] content) {
        String folder = "movehome/cancellations/%s".formatted(cancellationId);
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

    // Signed URL cho resource authenticated — vong doi bao mat dua JWT 15 phut (nhu DisputePhoto, AC-10).
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
