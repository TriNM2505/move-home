package vn.movehome.backend.blog;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.BlogPostResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test PublicBlogController — feed + binh luan cong khai (Guest xem duoc, HR-17).
 */
class PublicBlogControllerTest {

    private final BlogService blogService = mock(BlogService.class);
    private final PublicBlogController controller = new PublicBlogController(blogService);

    @Test
    void feed_uyThacChoBlogServiceVoiThamSoPhanTrang() {
        BlogPostResponse post = new BlogPostResponse(
                UUID.randomUUID(), "Nguyen Van A", null, "Nội dung", 5, List.of(), 0L, Instant.now());
        Page<BlogPostResponse> expected = new PageImpl<>(List.of(post), PageRequest.of(1, 20), 1);
        when(blogService.feed(1, 20)).thenReturn(expected);

        Page<BlogPostResponse> actual = controller.feed(1, 20);

        assertThat(actual).isEqualTo(expected);
        verify(blogService).feed(1, 20);
    }

    @Test
    void comments_uyThacChoBlogService() {
        UUID postId = UUID.randomUUID();
        List<BlogCommentResponse> expected = List.of(new BlogCommentResponse(
                UUID.randomUUID(), "Nguyen Van A", null, "CUSTOMER", "Cảm ơn shop", Instant.now()));
        when(blogService.listComments(postId)).thenReturn(expected);

        List<BlogCommentResponse> actual = controller.comments(postId);

        assertThat(actual).isEqualTo(expected);
        verify(blogService).listComments(postId);
    }
}
