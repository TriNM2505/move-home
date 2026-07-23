package vn.movehome.backend.blog.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test record CreateBlogCommentRequest — construct + accessor + equals/hashCode/toString.
 */
class CreateBlogCommentRequestTest {

    @Test
    void construct_traVeDungNoiDungQuaAccessor() {
        CreateBlogCommentRequest request = new CreateBlogCommentRequest("Cảm ơn shop nhé");

        assertThat(request.content()).isEqualTo("Cảm ơn shop nhé");
    }

    @Test
    void equalsVaHashCode_haiInstanceCungNoiDung() {
        CreateBlogCommentRequest a = new CreateBlogCommentRequest("Nội dung");
        CreateBlogCommentRequest b = new CreateBlogCommentRequest("Nội dung");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("Nội dung");
    }
}
