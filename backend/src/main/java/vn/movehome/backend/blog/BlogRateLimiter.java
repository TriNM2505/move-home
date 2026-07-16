package vn.movehome.backend.blog;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chong spam Blog cong dong (Pha C) — rate limit theo user, cua so truot 1 gio, luu in-memory.
 * Vuot han muc -> HTTP 429 (GlobalExceptionHandler map thanh error_code RATE_LIMITED).
 * Don gian cho do an: khong dung Redis/bang DB. Reset khi restart app (chap nhan duoc).
 */
@Component
public class BlogRateLimiter {

    private static final int MAX_POSTS_PER_HOUR = 5;
    private static final int MAX_COMMENTS_PER_HOUR = 20;
    private static final Duration WINDOW = Duration.ofHours(1);

    // key = userId + ":" + action -> cac moc thoi gian trong cua so
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Kiem tra truoc khi dang bai. Vuot 5 bai/gio -> 429. */
    public void checkPost(UUID userId) {
        check(userId + ":post", MAX_POSTS_PER_HOUR, "bài viết");
    }

    /** Kiem tra truoc khi binh luan. Vuot 20 binh luan/gio -> 429. */
    public void checkComment(UUID userId) {
        check(userId + ":comment", MAX_COMMENTS_PER_HOUR, "bình luận");
    }

    private void check(String key, int max, String label) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            // Bo cac moc da ra khoi cua so 1 gio
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= max) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMITED|Bạn đăng " + label + " quá nhanh. Vui lòng thử lại sau ít phút.");
            }
            window.addLast(now);
        }
    }
}
