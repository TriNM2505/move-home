package vn.movehome.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token đặt lại mật khẩu không được để trống.")
        @Size(max = 255, message = "Token đặt lại mật khẩu không hợp lệ.")
        String token,

        @NotBlank(message = "Mật khẩu mới không được để trống.")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự.")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&_.#^()\\-])[A-Za-z\\d@$!%*?&_.#^()\\-]{8,}$",
                message = "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt."
        )
        String newPassword
) {
}
