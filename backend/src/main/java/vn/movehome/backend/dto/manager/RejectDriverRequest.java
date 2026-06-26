package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RejectDriverRequest(
        String reason,
        @JsonProperty("decision_note") String decisionNote
) {}
