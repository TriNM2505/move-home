package vn.movehome.backend.blog;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.BlogPostResponse;
import vn.movehome.backend.blog.dto.CreateBlogCommentRequest;
import vn.movehome.backend.entity.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test CustomerBlogController — dang bai + binh luan (Customer only, HR-10).
 */
class CustomerBlogControllerTest {

    private final BlogService blogService = mock(BlogService.class);
    private final CustomerBlogController controller = new CustomerBlogController(blogService);

    @Test
    void createPost_chuaDangNhap_tra401() {
        MockMultipartFile file = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> controller.createPost(null, "Nội dung", 5, List.of(file)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }

    @Test
    void createPost_daDangNhap_uyThacChoBlogService() {
        User me = User.builder().id(UUID.randomUUID()).build();
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1}));
        BlogPostResponse expected = new BlogPostResponse(
                UUID.randomUUID(), "Nguyen Van A", null, "Nội dung", 5, List.of(), 0L, Instant.now());
        when(blogService.createPost(me, "Nội dung", 5, files)).thenReturn(expected);

        BlogPostResponse actual = controller.createPost(me, "Nội dung", 5, files);

        assertThat(actual).isEqualTo(expected);
        verify(blogService).createPost(me, "Nội dung", 5, files);
    }

    @Test
    void addComment_chuaDangNhap_tra401() {
        assertThatThrownBy(() -> controller.addComment(null, UUID.randomUUID(),
                new CreateBlogCommentRequest("Nội dung")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }

    @Test
    void addComment_daDangNhap_uyThacChoBlogService() {
        User me = User.builder().id(UUID.randomUUID()).build();
        UUID postId = UUID.randomUUID();
        BlogCommentResponse expected = new BlogCommentResponse(
                UUID.randomUUID(), "Nguyen Van A", null, "CUSTOMER", "Cảm ơn shop", Instant.now());
        when(blogService.addComment(me, postId, "Cảm ơn shop")).thenReturn(expected);

        BlogCommentResponse actual = controller.addComment(me, postId, new CreateBlogCommentRequest("Cảm ơn shop"));

        assertThat(actual).isEqualTo(expected);
        verify(blogService).addComment(me, postId, "Cảm ơn shop");
    }
}
