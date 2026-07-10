package vn.movehome.backend.dto.auth;

import jakarta.validation.constraints.*;

/**
 * Request body dang ky tai khoan Driver (CONTEXT §2 Driver Onboarding Buoc 1).
 * Field giong het Customer (thong tin ca nhan co ban); khac biet o cho tao User
 * voi role=DRIVER, status=PENDING_VERIFY va tao san 1 dong driver_profile rong.
 * Loi validate tra HTTP 422 voi danh sach field loi.
 */
public record RegisterDriverRequest(

    @NotBlank(message = "Email khong duoc de trong.")
    @Email(message = "Email khong dung dinh dang.")
    @Size(max = 255, message = "Email khong duoc vuot qua 255 ky tu.")
    String email,

    // 8-72 ky tu, >= 1 chu hoa, >= 1 chu thuong, >= 1 so, >= 1 ky tu dac biet
    @NotBlank(message = "Mat khau khong duoc de trong.")
    @Size(min = 8, max = 72, message = "Mat khau phai tu 8 den 72 ky tu.")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&_.#^()\\-])[A-Za-z\\d@$!%*?&_.#^()\\-]{8,}$",
        message = "Mat khau phai co it nhat 1 chu hoa, 1 chu thuong, 1 so, 1 ky tu dac biet."
    )
    String password,

    @NotBlank(message = "Ho ten khong duoc de trong.")
    @Size(min = 2, max = 100, message = "Ho ten phai tu 2 den 100 ky tu.")
    String fullName,

    // Nullable — neu cung cap phai dung dinh dang
    @Pattern(
        regexp = "^(\\+84|0)[0-9]{9,10}$",
        message = "So dien thoai khong dung dinh dang. Vi du: 0912345678 hoac +84912345678."
    )
    String phone,

    // Phai la true — tai xe phai dong y dieu khoan
    @AssertTrue(message = "Ban phai dong y dieu khoan va chinh sach bao mat de tao tai khoan.")
    boolean termsAccepted

) {}
