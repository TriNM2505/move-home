package vn.movehome.backend.dto.manager;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApproveDriverRequest(
        @JsonProperty("decision_note") String decisionNote
) {}
