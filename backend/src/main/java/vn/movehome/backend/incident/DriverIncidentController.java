package vn.movehome.backend.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.movehome.backend.entity.User;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint tai xe bao su co giua chuyen (V44). HR-10: chi role DRIVER.
 * Luong 2 buoc giong huy don: (1) tao bao cao lay incidentId, (2) upload tung anh (toi da 3).
 */
@RestController
@RequestMapping("/api/driver/orders")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverIncidentController {

    private final DriverIncidentService driverIncidentService;
    private final DriverIncidentPhotoService driverIncidentPhotoService;

    /** Tai xe bam "Bao su co" + mo ta loi. Tra ve incidentId de upload anh tiep theo. */
    @PostMapping("/{id}/incident")
    public ResponseEntity<Map<String, UUID>> reportIncident(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @RequestBody ReportIncidentRequest request
    ) {
        UUID incidentId = driverIncidentService.reportIncident(
                currentUser.getId(), id, request != null ? request.reason() : null);
        return ResponseEntity.ok(Map.of("incidentId", incidentId));
    }

    /** Dinh kem 1 anh bang chung cho bao cao su co dang PENDING cua don. Toi da 3 anh (service chan). */
    @PostMapping("/{id}/incident/photos")
    public ResponseEntity<Void> uploadPhoto(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        driverIncidentPhotoService.uploadByOrder(id, currentUser.getId(), file);
        return ResponseEntity.ok().build();
    }

    /** Body JSON cho buoc tao bao cao su co. */
    public record ReportIncidentRequest(String reason) {
    }
}
