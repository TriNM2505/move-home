package vn.movehome.backend.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDisputeRequest(
        @NotBlank
        @Size(min = 10, max = 1000)
        String note
) {
}
