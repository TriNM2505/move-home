package vn.movehome.backend.incident;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import vn.movehome.backend.entity.User;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra DriverIncidentController (V44) theo pattern DriverDocumentControllerTest:
 * khoi tao controller thuan voi mock service, khong load Spring context.
 */
class DriverIncidentControllerTest {

    private final DriverIncidentService driverIncidentService = mock(DriverIncidentService.class);
    private final DriverIncidentPhotoService driverIncidentPhotoService = mock(DriverIncidentPhotoService.class);
    private final DriverIncidentController controller =
            new DriverIncidentController(driverIncidentService, driverIncidentPhotoService);

    @Test
    void reportIncident_usesAuthenticatedPrincipalIdAndReturnsIncidentId() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        DriverIncidentController.ReportIncidentRequest request =
                new DriverIncidentController.ReportIncidentRequest("Xe hỏng giữa đường");
        when(driverIncidentService.reportIncident(driverId, orderId, "Xe hỏng giữa đường"))
                .thenReturn(incidentId);

        ResponseEntity<Map<String, UUID>> response = controller.reportIncident(currentUser, orderId, request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("incidentId", incidentId);
        verify(driverIncidentService).reportIncident(driverId, orderId, "Xe hỏng giữa đường");
    }

    @Test
    void reportIncident_whenRequestBodyNull_passesNullReason() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        when(driverIncidentService.reportIncident(eq(driverId), eq(orderId), isNull()))
                .thenReturn(UUID.randomUUID());

        controller.reportIncident(currentUser, orderId, null);

        verify(driverIncidentService).reportIncident(eq(driverId), eq(orderId), isNull());
    }

    @Test
    void uploadPhoto_usesAuthenticatedPrincipalIdAndDelegatesToPhotoService() {
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        MockMultipartFile file = new MockMultipartFile("file", "evidence.jpg", "image/jpeg", new byte[]{1, 2, 3});

        ResponseEntity<Void> response = controller.uploadPhoto(currentUser, orderId, file);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(driverIncidentPhotoService).uploadByOrder(orderId, driverId, file);
    }

    @Test
    void reportIncidentRequest_recordConstructAndAccessor() {
        DriverIncidentController.ReportIncidentRequest request =
                new DriverIncidentController.ReportIncidentRequest("Ly do bao su co");

        assertThat(request.reason()).isEqualTo("Ly do bao su co");
    }
}
