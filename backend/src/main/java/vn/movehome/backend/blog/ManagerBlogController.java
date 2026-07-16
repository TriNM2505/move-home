package vn.movehome.backend.blog;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.CreateBlogCommentRequest;
import vn.movehome.backend.entity.User;

import java.util.Map;
import java.util.UUID;

/**
 * Manager tra loi binh luan Blog cong dong (Pha B).
 * SecurityConfig: /api/manager/** yeu cau ROLE_MANAGER (HR-10). Binh luan cua Manager
 * duoc snapshot author_role = MANAGER → FE render badge "Quan ly".
 */
@RestController
@RequestMapping("/api/manager/blog")
@RequiredArgsConstructor
public class ManagerBlogController {

    private final BlogService blogService;

    @PostMapping("/posts/{postId}/comments")
    public BlogCommentResponse reply(
            @AuthenticationPrincipal User me,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateBlogCommentRequest request) {
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
        return blogService.addComment(me, postId, request.content());
    }

    // ===== Kiem duyet (Pha C) — chi MANAGER (SecurityConfig /api/manager/**) =====

    /** An 1 bai khoi feed cong khai. */
    @PostMapping("/posts/{postId}/hide")
    public Map<String, Object> hidePost(@PathVariable UUID postId) {
        blogService.setPostHidden(postId, true);
        return Map.of("success", true);
    }

    /** Hien lai 1 bai da an. */
    @PostMapping("/posts/{postId}/unhide")
    public Map<String, Object> unhidePost(@PathVariable UUID postId) {
        blogService.setPostHidden(postId, false);
        return Map.of("success", true);
    }

    /** Xoa han 1 bai (kem anh Cloudinary). */
    @DeleteMapping("/posts/{postId}")
    public Map<String, Object> deletePost(@PathVariable UUID postId) {
        blogService.deletePost(postId);
        return Map.of("success", true);
    }

    /** An 1 binh luan. */
    @PostMapping("/comments/{commentId}/hide")
    public Map<String, Object> hideComment(@PathVariable UUID commentId) {
        blogService.setCommentHidden(commentId, true);
        return Map.of("success", true);
    }

    /** Xoa han 1 binh luan. */
    @DeleteMapping("/comments/{commentId}")
    public Map<String, Object> deleteComment(@PathVariable UUID commentId) {
        blogService.deleteComment(commentId);
        return Map.of("success", true);
    }
}
