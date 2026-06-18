package vn.movehome.backend.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateOrderRequest(
        @NotBlank(message = "Loại xe không được để trống.")
        @Pattern(
                regexp = "TRUCK_500KG|TRUCK_1T|TRUCK_15T",
                message = "Loại xe không hợp lệ."
        )
        String vehicleType,

        @Valid
        @NotNull(message = "Thông tin điểm đón không được để trống.")
        Location pickup,

        @Valid
        @NotNull(message = "Thông tin điểm trả không được để trống.")
        Location dropoff,

        @NotNull(message = "Thời gian chuyển nhà không được để trống.")
        @Future(message = "Thời gian chuyển nhà phải ở trong tương lai.")
        OffsetDateTime scheduledAt,

        @Min(value = 0, message = "Số người bốc xếp phải từ 0 đến 3.")
        @Max(value = 3, message = "Số người bốc xếp phải từ 0 đến 3.")
        int porterCount,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự.")
        String notes
) {

    public record Location(
            @NotBlank(message = "Địa chỉ không được để trống.")
            @Size(min = 10, max = 200, message = "Địa chỉ phải có từ 10 đến 200 ký tự.")
            String address,

            @NotBlank(message = "Quận/huyện không được để trống.")
            @Pattern(
                    regexp = "BA_DINH|HOAN_KIEM|HAI_BA_TRUNG|DONG_DA|TAY_HO|CAU_GIAY|THANH_XUAN|LONG_BIEN|HA_DONG|HOANG_MAI|BAC_TU_LIEM|NAM_TU_LIEM",
                    message = "Quận/huyện không nằm trong khu vực hỗ trợ."
            )
            String district,

            @NotNull(message = "Vĩ độ không được để trống.")
            @DecimalMin(value = "-90.0", message = "Vĩ độ không hợp lệ.")
            @DecimalMax(value = "90.0", message = "Vĩ độ không hợp lệ.")
            BigDecimal lat,

            @NotNull(message = "Kinh độ không được để trống.")
            @DecimalMin(value = "-180.0", message = "Kinh độ không hợp lệ.")
            @DecimalMax(value = "180.0", message = "Kinh độ không hợp lệ.")
            BigDecimal lng,

            @Min(value = 0, message = "Số tầng phải từ 0 đến 30.")
            @Max(value = 30, message = "Số tầng phải từ 0 đến 30.")
            int floor,

            boolean hasElevator,

            boolean hasAlley
    ) {
    }
}
