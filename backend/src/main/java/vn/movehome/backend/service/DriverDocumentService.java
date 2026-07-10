package vn.movehome.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;
import vn.movehome.backend.dto.driver.DriverDocumentResponse;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.repository.DriverDocumentRepository;

@Service
@RequiredArgsConstructor
public class DriverDocumentService {

    static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "DRIVING_LICENSE_FRONT",
            "DRIVING_LICENSE_BACK",
            "VEHICLE_REGISTRATION_FRONT",
            "VEHICLE_REGISTRATION_BACK",
            "VEHICLE_PHOTO_FRONT",
            "VEHICLE_PHOTO_REAR",
            "VEHICLE_PHOTO_SIDE",
            "FACE_PHOTO");
    static final long MAX_FILE_SIZE = 1_572_864L;

    private final DriverDocumentRepository driverDocumentRepository;
    private final Cloudinary cloudinary;

    @Transactional
    public DriverDocumentResponse upload(UUID driverId, String requestedDocType, MultipartFile file) {
        String docType = validateDocumentType(requestedDocType);
        byte[] content = validateAndReadImage(file);
        CloudinaryUploadResult uploadResult = uploadToCloudinary(driverId, docType, content);

        // Moi loai giay to chi giu 1 ban moi nhat: xoa cac ban cu cung loai truoc khi luu ban moi.
        // Tranh cong don khi tai xe upload lai nhieu lan (hien trung anh o trang duyet).
        driverDocumentRepository.deleteByDriverIdAndDocType(driverId, docType);

        DriverDocument saved = driverDocumentRepository.save(DriverDocument.builder()
                .driverId(driverId)
                .docType(docType)
                .url(uploadResult.secureUrl())
                .publicId(uploadResult.publicId())
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DriverDocumentResponse> getMyDocuments(UUID driverId) {
        return driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Tra ve signed URL moi nhat cho tung loai giay to yeu cau (map docType -> url).
     * Dung cho khach xem anh chan dung + anh xe cua tai xe da nhan don de doi chieu.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, String> latestSignedUrlsByType(UUID driverId, Set<String> docTypes) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (DriverDocument doc : driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId)) {
            if (docTypes.contains(doc.getDocType()) && !result.containsKey(doc.getDocType())) {
                result.put(doc.getDocType(), resolveSignedUrl(doc));
            }
        }
        return result;
    }

    /** Signed URL neu co public_id (data moi), nguoc lai tra URL tho (data cu). */
    private String resolveSignedUrl(DriverDocument document) {
        if (document.getPublicId() != null && !document.getPublicId().isBlank()) {
            return signUrl(document.getPublicId());
        }
        return document.getUrl();
    }

    private String validateDocumentType(String requestedDocType) {
        if (requestedDocType == null || requestedDocType.isBlank()) {
            throw invalidDocumentType();
        }

        String normalized = requestedDocType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_DOCUMENT_TYPES.contains(normalized)) {
            throw invalidDocumentType();
        }
        return normalized;
    }

    private ResponseStatusException invalidDocumentType() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "INVALID_DOCUMENT_TYPE|Loại tài liệu không hợp lệ. Chỉ chấp nhận giấy phép lái xe, đăng ký xe hoặc ảnh xe.");
    }

    private byte[] validateAndReadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile("Tệp tải lên không được để trống.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalidFile("Kích thước tệp không được vượt quá 1,5 MB.");
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
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_FILE|" + message);
    }

    private CloudinaryUploadResult uploadToCloudinary(UUID driverId, String docType, byte[] content) {
        String folder = "movehome/drivers/%s/documents/%s".formatted(driverId, docType);
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
                    "CLOUDINARY_UNAVAILABLE|Không thể tải tài liệu lên Cloudinary. Vui lòng thử lại.", ex);
        }
    }

    /**
     * Generate signed URL cho tai lieu nhay cam (GPLX, dang ky xe).
     * Spec 008 yeu cau TTL 1h — Cloudinary free plan khong support TTL truc tiep o
     * URL level
     * (chi Advanced plan moi co AuthToken). Implement signed URL + security
     * boundary
     * qua JWT lifecycle (15 phut access token expire).
     * Resource type "authenticated" -> bat buoc co chu ky de access.
     */
    String signUrl(String publicId) {
        return cloudinary.url()
                .resourceType("image")
                .type("authenticated")
                .secure(true)
                .signed(true)
                .generate(publicId);
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (unsigned(bytes[i]) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && ascii(bytes, 0, 4).equals("RIFF")
                && ascii(bytes, 8, 4).equals("WEBP");
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    /**
     * Build response DTO voi rule fallback:
     * - publicId co (data moi) -> tra signed URL
     * - publicId null (data cu truoc khi co Cloudinary that) -> tra URL tho (Leader
     * chot)
     */
    private DriverDocumentResponse toResponse(DriverDocument document) {
        String responseUrl;
        if (document.getPublicId() != null && !document.getPublicId().isBlank()) {
            responseUrl = signUrl(document.getPublicId());
        } else {
            responseUrl = document.getUrl();
        }
        return new DriverDocumentResponse(
                document.getId(),
                document.getDocType(),
                responseUrl,
                document.getUploadedAt());
    }

    /** Internal record gom secure_url + public_id tu Cloudinary upload response. */
    private record CloudinaryUploadResult(String secureUrl, String publicId) {
    }
}