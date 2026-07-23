package vn.movehome.backend.blog;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test BlogRateLimiter — chong spam dang bai/binh luan Blog cong dong (Pha C).
 */
class BlogRateLimiterTest {

    private final BlogRateLimiter limiter = new BlogRateLimiter();

    @Test
    void checkPost_duoiHanMuc_khongNem() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            limiter.checkPost(userId);
        }
        // 5 bai/gio la muc toi da -> khong nem loi o lan thu 5
    }

    @Test
    void checkPost_vuotHanMuc5BaiMoiGio_tra429() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            limiter.checkPost(userId);
        }

        assertThatThrownBy(() -> limiter.checkPost(userId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo(
                            "RATE_LIMITED|Bạn đăng bài viết quá nhanh. Vui lòng thử lại sau ít phút.");
                });
    }

    @Test
    void checkComment_vuotHanMuc20BinhLuanMoiGio_tra429() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            limiter.checkComment(userId);
        }

        assertThatThrownBy(() -> limiter.checkComment(userId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo(
                            "RATE_LIMITED|Bạn đăng bình luận quá nhanh. Vui lòng thử lại sau ít phút.");
                });
    }

    @Test
    void check_haiUserKhacNhau_khongAnhHuongLanNhau() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            limiter.checkPost(userA);
        }

        // userB chua dang bai lan nao -> khong bi anh huong boi gioi han cua userA
        limiter.checkPost(userB);
    }

    @Test
    void check_cacMocThoiGianCuHonCuaSoTruot_biLoaiBoTruocKhiDem() throws Exception {
        UUID userId = UUID.randomUUID();
        // Chen truoc 5 moc thoi gian tu 2 gio truoc (ngoai cua so truot 1 gio) bang reflection,
        // mo phong truong hop cac lan dang bai cu da het han rate-limit.
        Field hitsField = BlogRateLimiter.class.getDeclaredField("hits");
        hitsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Instant>> hits = (Map<String, Deque<Instant>>) hitsField.get(limiter);
        Deque<Instant> oldWindow = new ArrayDeque<>();
        Instant twoHoursAgo = Instant.now().minusSeconds(7200);
        for (int i = 0; i < 5; i++) {
            oldWindow.addLast(twoHoursAgo);
        }
        hits.put(userId + ":post", oldWindow);

        // Neu cac moc cu khong duoc don dep, lan goi nay se vuot han muc (>=5) va nem 429 ngay.
        // Vi cua so truot da loai bo cac moc qua han, request nay van duoc chap nhan.
        limiter.checkPost(userId);

        Deque<Instant> windowAfter = hits.get(userId + ":post");
        assertThat(windowAfter).hasSize(1); // moc cu bi don, chi con moc vua them
    }
}
