package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.movehome.backend.dto.driver.DriverDocumentResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.DriverDocumentService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverDocumentControllerTest {

    private final DriverDocumentService service = mock(DriverDocumentService.class);
    private final DriverDocumentController controller = new DriverDocumentController(service);

    @Test
    void uploadUsesAuthenticatedPrincipalIdInsteadOfRequestOwnershipData() {
        UUID driverId = UUID.randomUUID();
        User currentDriver = User.builder().id(driverId).build();
        MockMultipartFile file = new MockMultipartFile(
                "file", "vehicle.png", "image/png", new byte[]{1, 2, 3});
        DriverDocumentResponse expected = new DriverDocumentResponse(
                UUID.randomUUID(),
                "VEHICLE_PHOTO",
                "https://res.cloudinary.com/demo/image/upload/vehicle.png",
                OffsetDateTime.now());
        when(service.upload(driverId, "VEHICLE_PHOTO", file)).thenReturn(expected);

        DriverDocumentResponse actual = controller.upload(currentDriver, file, "VEHICLE_PHOTO");

        assertThat(actual).isEqualTo(expected);
        verify(service).upload(driverId, "VEHICLE_PHOTO", file);
    }

    @Test
    void getMyDocumentsUsesAuthenticatedPrincipalId() {
        UUID driverId = UUID.randomUUID();
        User currentDriver = User.builder().id(driverId).build();
        List<DriverDocumentResponse> expected = List.of(new DriverDocumentResponse(
                UUID.randomUUID(),
                "DRIVING_LICENSE",
                "https://res.cloudinary.com/demo/image/upload/license.jpg",
                OffsetDateTime.now()));
        when(service.getMyDocuments(driverId)).thenReturn(expected);

        assertThat(controller.getMyDocuments(currentDriver)).isEqualTo(expected);
        verify(service).getMyDocuments(driverId);
    }
}
