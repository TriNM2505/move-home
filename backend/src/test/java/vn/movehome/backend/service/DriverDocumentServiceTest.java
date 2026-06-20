package vn.movehome.backend.service;

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
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.driver.DriverDocumentResponse;
import vn.movehome.backend.entity.DriverDocument;
import vn.movehome.backend.repository.DriverDocumentRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverDocumentServiceTest {

    @Mock
    private DriverDocumentRepository driverDocumentRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private DriverDocumentService service;

    @BeforeEach
    void setUp() {
        service = new DriverDocumentService(driverDocumentRepository, cloudinary);
    }

    @Test
    void uploadStoresDocumentForAuthenticatedDriverAndReturnsSecureUrl() throws Exception {
        UUID driverId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = validJpeg();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/license.jpg"));
        when(driverDocumentRepository.save(any(DriverDocument.class))).thenAnswer(invocation -> {
            DriverDocument document = invocation.getArgument(0);
            document.setId(documentId);
            return document;
        });

        DriverDocumentResponse response = service.upload(driverId, " driving_license ", file);

        ArgumentCaptor<DriverDocument> documentCaptor = ArgumentCaptor.forClass(DriverDocument.class);
        verify(driverDocumentRepository).save(documentCaptor.capture());
        DriverDocument saved = documentCaptor.getValue();

        assertThat(saved.getDriverId()).isEqualTo(driverId);
        assertThat(saved.getDocType()).isEqualTo("DRIVING_LICENSE");
        assertThat(saved.getUrl()).startsWith("https://res.cloudinary.com/");
        assertThat(saved.getUploadedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.id()).isEqualTo(documentId);
        assertThat(response.docType()).isEqualTo("DRIVING_LICENSE");
        assertThat(response.url()).isEqualTo(saved.getUrl());
    }

    @Test
    void uploadRejectsUnsupportedDocumentTypeWithBadRequest() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upload(driverId, "IDENTITY_CARD", validJpeg()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).startsWith("INVALID_DOCUMENT_TYPE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsFileWhoseContentIsNotARealSupportedImage() {
        UUID driverId = UUID.randomUUID();
        MockMultipartFile renamedTextFile = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "không phải ảnh".getBytes());

        assertThatThrownBy(() -> service.upload(driverId, "VEHICLE_PHOTO", renamedTextFile))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).startsWith("INVALID_FILE|");
                });

        verify(cloudinary, never()).uploader();
        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    void getMyDocumentsQueriesOnlyAuthenticatedDriverId() {
        UUID driverId = UUID.randomUUID();
        OffsetDateTime uploadedAt = OffsetDateTime.now(ZoneOffset.UTC);
        DriverDocument document = DriverDocument.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .docType("VEHICLE_REGISTRATION")
                .url("https://res.cloudinary.com/demo/image/upload/registration.png")
                .uploadedAt(uploadedAt)
                .build();
        when(driverDocumentRepository.findByDriverIdOrderByUploadedAtDesc(driverId))
                .thenReturn(List.of(document));

        List<DriverDocumentResponse> result = service.getMyDocuments(driverId);

        assertThat(result).containsExactly(new DriverDocumentResponse(
                document.getId(), document.getDocType(), document.getUrl(), uploadedAt));
        verify(driverDocumentRepository).findByDriverIdOrderByUploadedAtDesc(driverId);
    }

    private MockMultipartFile validJpeg() {
        return new MockMultipartFile(
                "file",
                "license.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
    }
}
