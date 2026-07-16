package vn.movehome.backend.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body tao 1 binh luan Blog cong dong. ES-03: @Valid + Bean Validation → 422 khi vi pham.
 */
public record CreateBlogCommentRequest(
        @NotBlank(message = "Nội dung bình luận không được để trống.")
        @Size(max = 1000, message = "Nội dung bình luận tối đa 1000 ký tự.")
        String content) {
}
