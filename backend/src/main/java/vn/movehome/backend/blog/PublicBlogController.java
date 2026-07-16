package vn.movehome.backend.blog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.BlogPostResponse;

import java.util.List;
import java.util.UUID;

/**
 * Feed Blog cong dong — PUBLIC (Guest xem duoc, khong can JWT).
 * HR-17: prefix /api/public/** (SecurityConfig permitAll). Chi tra du lieu cong khai,
 * KHONG tra PII (email/phone) — xem BlogService.toResponse.
 */
@RestController
@RequestMapping("/api/public/blog")
@RequiredArgsConstructor
public class PublicBlogController {

    private final BlogService blogService;

    /** Danh sach bai dang VISIBLE, moi nhat truoc. Server-side pagination (AC-15). */
    @GetMapping("/feed")
    public Page<BlogPostResponse> feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return blogService.feed(page, size);
    }

    /** Binh luan VISIBLE cua 1 bai (cu -> moi). Guest doc duoc (Pha B). */
    @GetMapping("/posts/{postId}/comments")
    public List<BlogCommentResponse> comments(@PathVariable UUID postId) {
        return blogService.listComments(postId);
    }
}
