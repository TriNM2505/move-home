package vn.movehome.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body gui 1 tin nhan. Bean Validation → HTTP 422 khi vi pham (ES-03).
 */
public record SendMessageRequest(
        @NotBlank(message = "Noi dung tin nhan khong duoc de trong.")
        @Size(max = 2000, message = "Tin nhan toi da 2000 ky tu.")
        String content
) {
}
