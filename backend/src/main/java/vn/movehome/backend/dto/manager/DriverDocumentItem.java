package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

// TODO Sprint 6: replace url with signed Cloudinary URL TTL 1h.
public record DriverDocumentItem(
        UUID id,
        @JsonProperty("doc_type") String docType,
        String url,
        @JsonProperty("uploaded_at") OffsetDateTime uploadedAt
) {}
