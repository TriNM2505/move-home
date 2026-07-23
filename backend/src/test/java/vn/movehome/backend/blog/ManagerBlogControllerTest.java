package vn.movehome.backend.blog;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.CreateBlogCommentRequest;
import vn.movehome.backend.entity.User;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test ManagerBlogController — Manager tra loi binh luan (Pha B) + kiem duyet (Pha C).
 */
class ManagerBlogControllerTest {

    private final BlogService blogService = mock(BlogService.class);
    private final ManagerBlogController controller = new ManagerBlogController(blogService);

    @Test
    void reply_chuaDangNhap_tra401() {
        assertThatThrownBy(() -> controller.reply(null, UUID.randomUUID(),
                new CreateBlogCommentRequest("Cảm ơn bạn")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }

    @Test
    void reply_daDangNhap_uyThacChoBlogService() {
        User manager = User.builder().id(UUID.randomUUID()).build();
        UUID postId = UUID.randomUUID();
        BlogCommentResponse expected = new BlogCommentResponse(
                UUID.randomUUID(), "Quan Ly", null, "MANAGER", "Cảm ơn bạn", Instant.now());
        when(blogService.addComment(manager, postId, "Cảm ơn bạn")).thenReturn(expected);

        BlogCommentResponse actual = controller.reply(manager, postId, new CreateBlogCommentRequest("Cảm ơn bạn"));

        assertThat(actual).isEqualTo(expected);
        verify(blogService).addComment(manager, postId, "Cảm ơn bạn");
    }

    @Test
    void hidePost_goiServiceVaTraVeSuccess() {
        UUID postId = UUID.randomUUID();

        Map<String, Object> result = controller.hidePost(postId);

        assertThat(result).isEqualTo(Map.of("success", true));
        verify(blogService).setPostHidden(postId, true);
    }

    @Test
    void unhidePost_goiServiceVaTraVeSuccess() {
        UUID postId = UUID.randomUUID();

        Map<String, Object> result = controller.unhidePost(postId);

        assertThat(result).isEqualTo(Map.of("success", true));
        verify(blogService).setPostHidden(postId, false);
    }

    @Test
    void deletePost_goiServiceVaTraVeSuccess() {
        UUID postId = UUID.randomUUID();

        Map<String, Object> result = controller.deletePost(postId);

        assertThat(result).isEqualTo(Map.of("success", true));
        verify(blogService).deletePost(postId);
    }

    @Test
    void hideComment_goiServiceVaTraVeSuccess() {
        UUID commentId = UUID.randomUUID();

        Map<String, Object> result = controller.hideComment(commentId);

        assertThat(result).isEqualTo(Map.of("success", true));
        verify(blogService).setCommentHidden(commentId, true);
    }

    @Test
    void deleteComment_goiServiceVaTraVeSuccess() {
        UUID commentId = UUID.randomUUID();

        Map<String, Object> result = controller.deleteComment(commentId);

        assertThat(result).isEqualTo(Map.of("success", true));
        verify(blogService).deleteComment(commentId);
    }
}
