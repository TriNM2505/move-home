package vn.movehome.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverDocumentService {

    static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "DRIVING_LICENSE",
            "VEHICLE_REGISTRATION",
            "VEHICLE_PHOTO"
    );
    static final long MAX_FILE_SIZE = 1_572_864L;

    private final DriverDocumentRepository driverDocumentRepository;
    private final Cloudinary cloudinary;

    public DriverDocumentResponse upload(UUID driverId, String requestedDocType, MultipartFile file) {
        String docType = validateDocumentType(requestedDocType);
        byte[] content = validateAndReadImage(file);
        String url = uploadToCloudinary(driverId, docType, content);

        DriverDocument saved = driverDocumentRepository.save(DriverDocument.builder()
                .driverId(driverId)
                .docType(docType)
                .url(url)
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

    private String uploadToCloudinary(UUID driverId, String docType, byte[] content) {
        String folder = "movehome/drivers/%s/documents/%s".formatted(driverId, docType);
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", folder
            ));
            Object secureUrl = uploadResult.get("secure_url");
            if (!(secureUrl instanceof String url) || !url.startsWith("https://")) {
                throw new IOException("Cloudinary không trả về secure_url hợp lệ");
            }
            return url;
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "CLOUDINARY_UNAVAILABLE|Không thể tải tài liệu lên Cloudinary. Vui lòng thử lại.", ex);
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
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

    private DriverDocumentResponse toResponse(DriverDocument document) {
        return new DriverDocumentResponse(
                document.getId(),
                document.getDocType(),
                document.getUrl(),
                document.getUploadedAt()
        );
    }
}
