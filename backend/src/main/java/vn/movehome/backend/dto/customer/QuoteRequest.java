package vn.movehome.backend.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuoteRequest(

        @NotBlank(message = "Loại xe không được để trống.")
        String vehicleType,

        @Valid
        @NotNull(message = "Thông tin điểm lấy hàng không được để trống.")
        Location pickup,

        @Valid
        @NotNull(message = "Thông tin điểm giao hàng không được để trống.")
        Location dropoff,

        @NotNull(message = "Thời gian chuyển nhà không được để trống.")
        Instant scheduledAt,

        @Min(value = 0, message = "Số bốc xếp không được âm.")
        int porterCount
) {

    public record Location(

            @NotBlank(message = "Địa chỉ không được để trống.")
            String address,

            @NotBlank(message = "Quận/huyện không được để trống.")
            String district,

            @NotNull(message = "Vĩ độ không được để trống.")
            @DecimalMin(value = "-90.0", message = "Vĩ độ không hợp lệ.")
            @DecimalMax(value = "90.0", message = "Vĩ độ không hợp lệ.")
            BigDecimal lat,

            @NotNull(message = "Kinh độ không được để trống.")
            @DecimalMin(value = "-180.0", message = "Kinh độ không hợp lệ.")
            @DecimalMax(value = "180.0", message = "Kinh độ không hợp lệ.")
            BigDecimal lng,

            @Min(value = 0, message = "Số tầng không được âm.")
            int floor,

            boolean hasElevator,

            boolean hasAlley
    ) {
    }
}
