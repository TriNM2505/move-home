package vn.movehome.backend.blog;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.BlogPostResponse;
import vn.movehome.backend.blog.dto.CreateBlogCommentRequest;
import vn.movehome.backend.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * Dang bai Blog cong dong — chi Customer (SecurityConfig: /api/customer/** hasRole('CUSTOMER'), HR-10).
 * Multipart: content (bat buoc) + rating (tuy chon) + files (toi da 3 anh).
 */
@RestController
@RequestMapping("/api/customer/blog")
@RequiredArgsConstructor
public class CustomerBlogController {

    private final BlogService blogService;

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BlogPostResponse createPost(
            @AuthenticationPrincipal User me,
            @RequestParam("content") String content,
            @RequestParam(value = "rating", required = false) Integer rating,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
        return blogService.createPost(me, content, rating, files);
    }

    /** Customer binh luan duoi 1 bai (Pha B). */
    @PostMapping("/posts/{postId}/comments")
    public BlogCommentResponse addComment(
            @AuthenticationPrincipal User me,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateBlogCommentRequest request) {
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
        return blogService.addComment(me, postId, request.content());
    }
}
