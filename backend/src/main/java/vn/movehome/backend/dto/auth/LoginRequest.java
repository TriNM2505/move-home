package vn.movehome.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body dang nhap (FR-017).
 * Hien tai chi ho tro dang nhap bang email (Round 2).
 * Spec FR-017 day du: Customer dung username (khong co @), Driver/Staff dung email (co @) —
 * se trien khai trong Round 3 khi them truong username vao User entity.
 */
public record LoginRequest(

    @NotBlank(message = "Email khong duoc de trong.")
    String email,

    @NotBlank(message = "Mat khau khong duoc de trong.")
    String password

) {}
