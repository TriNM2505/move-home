package vn.movehome.backend.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnPayIpnResponse(
        @JsonProperty("RspCode")
        String rspCode,
        @JsonProperty("Message")
        String message
) {
}
