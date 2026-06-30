package vn.movehome.backend.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import vn.movehome.backend.dto.manager.DriverDetailResponse;
import vn.movehome.backend.dto.manager.DriverDocumentItem;
import vn.movehome.backend.dto.manager.PendingDriverItem;
import vn.movehome.backend.dto.manager.RejectedDriverItem;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.repository.DriverDocumentRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerDriverQueryService {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final DriverDocumentService driverDocumentService;

    public Page<PendingDriverItem> findPendingApproval(int page, int size) {
        Pageable pageable = PageRequest.of(
                clampPage(page),
                clampSize(size),
                Sort.by(Sort.Direction.ASC, "onboardingCompletedAt"));

        return driverProfileRepository.findPendingApproval(pageable)
                .map(this::toPendingItem);
    }

    public Page<RejectedDriverItem> findRejected(int page, int size) {
        Pageable pageable = PageRequest.of(
                clampPage(page),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));

        return driverProfileRepository.findRejected(pageable)
                .map(this::toRejectedItem);
    }

    public DriverDetailResponse getDetail(UUID driverId) {
        User user = loadDriver(driverId);

        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));

        long documentCount = driverDocumentRepository.countByDriverId(driverId);

        return new DriverDetailResponse(
                driverId,
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus().name(),
                profile.getLicenseNumber(),
                profile.getLicenseClass(),
                profile.getLicenseExpiryDate(),
                profile.getVehiclePlate(),
                profile.getVehicleType(),
                profile.getVehicleCapacityKg(),
                profile.getDepositAmount(),
                profile.getDepositPaidAt(),
                profile.getOnboardingCompletedAt(),
                profile.getApprovedAt(),
                profile.getApprovedByManagerId(),
                user.getRejectionReason(),
                documentCount);
    }

    public List<DriverDocumentItem> getDocuments(UUID driverId) {
        loadDriver(driverId);

        List<DriverDocument> documents = driverDocumentRepository
                .findByDriverIdOrderByUploadedAtDesc(driverId);

        return documents.stream()
                .map(doc -> new DriverDocumentItem(
                        doc.getId(),
                        doc.getDocType(),
                        resolveUrl(doc),
                        doc.getUploadedAt()))
                .toList();
    }

    /**
     * Rule fallback Leader chot:
     * - publicId co (data moi) -> tra signed URL
     * - publicId null (data cu) -> tra URL tho
     */
    private String resolveUrl(DriverDocument doc) {
        if (doc.getPublicId() != null && !doc.getPublicId().isBlank()) {
            return driverDocumentService.signUrl(doc.getPublicId());
        }
        return doc.getUrl();
    }

    private User loadDriver(UUID driverId) {
        User user = userRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));

        if (user.getRole() != UserRole.DRIVER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DRIVER_NOT_FOUND|Không tìm thấy tài xế.");
        }

        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DRIVER_NOT_FOUND|Không tìm thấy tài xế.");
        }

        return user;
    }

    private PendingDriverItem toPendingItem(DriverProfile profile) {
        User user = userRepository.findById(profile.getUserId()).orElse(null);
        long documentCount = driverDocumentRepository.countByDriverId(profile.getUserId());

        return new PendingDriverItem(
                profile.getUserId(),
                user != null ? user.getFullName() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getPhone() : null,
                profile.getVehiclePlate(),
                profile.getVehicleType(),
                profile.getDepositAmount(),
                profile.getDepositPaidAt(),
                profile.getOnboardingCompletedAt(),
                documentCount);
    }

    private RejectedDriverItem toRejectedItem(DriverProfile profile) {
        User user = userRepository.findById(profile.getUserId()).orElse(null);

        return new RejectedDriverItem(
                profile.getUserId(),
                user != null ? user.getFullName() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getPhone() : null,
                user != null ? user.getRejectionReason() : null,
                user != null ? toOffsetDateTime(user.getUpdatedAt()) : null);
    }

    private int clampPage(int page) {
        return Math.max(0, page);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}