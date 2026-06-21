package vn.movehome.backend.driver.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateDriverLocationRequest(
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        @Digits(integer = 2, fraction = 7)
        BigDecimal lat,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        @Digits(integer = 3, fraction = 7)
        BigDecimal lng,

        @DecimalMin("0")
        @DecimalMax(value = "360", inclusive = false)
        @Digits(integer = 3, fraction = 2)
        BigDecimal heading,

        @JsonProperty("speed_kmh")
        @DecimalMin("0")
        @DecimalMax("180")
        @Digits(integer = 3, fraction = 2)
        BigDecimal speedKmh
) {
}
