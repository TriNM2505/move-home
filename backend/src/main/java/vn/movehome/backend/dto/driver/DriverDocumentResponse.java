package vn.movehome.backend.dto.driver;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverDocumentResponse(
        UUID id,
        @JsonProperty("doc_type") String docType,
        String url,
        @JsonProperty("uploaded_at") OffsetDateTime uploadedAt
) {
}
