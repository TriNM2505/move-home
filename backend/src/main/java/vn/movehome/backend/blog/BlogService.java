package vn.movehome.backend.blog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.BlogPostResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nghiep vu Blog cong dong: dang bai + feed (Pha A) va binh luan + Manager tra loi (Pha B).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String STATUS_VISIBLE = "VISIBLE";
    private static final String NOTIF_TYPE_BLOG_COMMENT = "BLOG_COMMENT";

    private final BlogPostRepository postRepository;
    private final BlogPostPhotoRepository photoRepository;
    private final BlogCommentRepository commentRepository;
    private final BlogPhotoService photoService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final BlogRateLimiter rateLimiter;

    // ===================== Bai dang (Pha A) =====================

    /**
     * Customer dang 1 bai review kem anh (toi da 3).
     * Chi tai khoan ACTIVE + da xac thuc email moi duoc dang (chong spam co ban — Pha A).
     */
    @Transactional
    public BlogPostResponse createPost(User author, String content, Integer rating, List<MultipartFile> files) {
        requireActiveCustomer(author);
        String trimmed = requireContent(content);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RATING|Đánh giá phải từ 1 đến 5 sao.");
        }
        rateLimiter.checkPost(author.getId()); // chong spam (Pha C)

        BlogPost post = postRepository.save(BlogPost.builder()
                .authorId(author.getId())
                .content(trimmed)
                .rating(rating == null ? null : rating.shortValue()) // cot SMALLINT (V42)
                .status(STATUS_VISIBLE)
                .build());

        List<BlogPostPhoto> photos = photoService.attachPhotos(post.getId(), author.getId(), files);
        List<String> photoUrls = photos.stream().map(BlogPostPhoto::getUrl).toList();

        log.info("Blog: customer {} dang bai {} ({} anh)", author.getId(), post.getId(), photoUrls.size());
        return toResponse(post, author, photoUrls, 0L);
    }

    /** Feed cong khai (bai VISIBLE, moi nhat truoc). Server-side pagination (AC-15). */
    @Transactional(readOnly = true)
    public Page<BlogPostResponse> feed(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<BlogPost> posts = postRepository.findVisible(pageable);
        List<BlogPost> content = posts.getContent();
        if (content.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, posts.getTotalElements());
        }

        // Batch load tac gia + anh + so binh luan (tranh N+1)
        Set<UUID> authorIds = content.stream().map(BlogPost::getAuthorId).collect(Collectors.toSet());
        Map<UUID, User> authors = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<UUID> postIds = content.stream().map(BlogPost::getId).collect(Collectors.toSet());
        Map<UUID, List<String>> photosByPost = photoRepository.findByPostIdInOrderByUploadedAtAsc(postIds).stream()
                .collect(Collectors.groupingBy(
                        BlogPostPhoto::getPostId,
                        Collectors.mapping(BlogPostPhoto::getUrl, Collectors.toList())));

        Map<UUID, Long> commentCountByPost = new HashMap<>();
        for (Object[] row : commentRepository.countVisibleByPostIds(postIds)) {
            commentCountByPost.put((UUID) row[0], (Long) row[1]);
        }

        List<BlogPostResponse> items = content.stream()
                .map(p -> toResponse(p, authors.get(p.getAuthorId()),
                        photosByPost.getOrDefault(p.getId(), List.of()),
                        commentCountByPost.getOrDefault(p.getId(), 0L)))
                .toList();

        return new PageImpl<>(items, pageable, posts.getTotalElements());
    }

    // ===================== Binh luan (Pha B) =====================

    /**
     * Them 1 binh luan. Customer (ACTIVE + verified) hoac Manager (tra loi). Bao cho chu bai
     * (neu nguoi binh luan khong phai chu bai) qua NotificationService.
     */
    @Transactional
    public BlogCommentResponse addComment(User author, UUID postId, String content) {
        boolean isManager = author.getRole() == UserRole.MANAGER;
        boolean isCustomer = author.getRole() == UserRole.CUSTOMER;
        if (!isManager && !isCustomer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN|Chỉ khách hàng hoặc quản lý mới được bình luận.");
        }
        if (isCustomer) {
            requireActiveCustomer(author);
        }
        String trimmed = requireContent(content);

        BlogPost post = postRepository.findById(postId)
                .filter(p -> STATUS_VISIBLE.equals(p.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "POST_NOT_FOUND|Không tìm thấy bài viết."));
        rateLimiter.checkComment(author.getId()); // chong spam (Pha C)

        BlogComment saved = commentRepository.save(BlogComment.builder()
                .postId(postId)
                .authorId(author.getId())
                .authorRole(author.getRole().name())
                .content(trimmed)
                .status(STATUS_VISIBLE)
                .build());

        // Bao cho chu bai — khong bao cho chinh minh. Loi notification KHONG lam hong binh luan (tinh than HR-11).
        if (!post.getAuthorId().equals(author.getId())) {
            try {
                String snippet = trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
                notificationService.create(post.getAuthorId(), NOTIF_TYPE_BLOG_COMMENT,
                        "Có phản hồi mới trên bài viết của bạn",
                        "%s đã phản hồi: %s".formatted(author.getFullName(), snippet));
            } catch (RuntimeException ex) {
                log.warn("Không tạo được thông báo bình luận blog cho bài {}: {}", postId, ex.getMessage());
            }
        }

        log.info("Blog: {} {} binh luan bai {}", author.getRole(), author.getId(), postId);
        return toCommentResponse(saved, author);
    }

    /** Danh sach binh luan VISIBLE cua 1 bai (cu -> moi). Public doc duoc. */
    @Transactional(readOnly = true)
    public List<BlogCommentResponse> listComments(UUID postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "POST_NOT_FOUND|Không tìm thấy bài viết."));

        List<BlogComment> comments = commentRepository.findVisibleByPost(postId);
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<UUID> authorIds = comments.stream().map(BlogComment::getAuthorId).collect(Collectors.toSet());
        Map<UUID, User> authors = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return comments.stream()
                .map(c -> toCommentResponse(c, authors.get(c.getAuthorId())))
                .toList();
    }

    // ===================== Kiem duyet (Pha C) — Manager =====================

    /** An/hien 1 bai (status HIDDEN/VISIBLE). Bai HIDDEN khong con tren feed cong khai. */
    @Transactional
    public void setPostHidden(UUID postId, boolean hidden) {
        BlogPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "POST_NOT_FOUND|Không tìm thấy bài viết."));
        post.setStatus(hidden ? "HIDDEN" : STATUS_VISIBLE);
        postRepository.save(post);
        log.info("Blog moderation: bai {} -> {}", postId, post.getStatus());
    }

    /** Xoa han 1 bai (soft delete) + destroy anh Cloudinary (AC-10 cleanup). */
    @Transactional
    public void deletePost(UUID postId) {
        BlogPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "POST_NOT_FOUND|Không tìm thấy bài viết."));
        photoService.deletePhotosByPost(postId);
        postRepository.delete(post); // @SQLDelete -> set deleted_at (AC-09)
        log.info("Blog moderation: xoa bai {}", postId);
    }

    /** An/hien 1 binh luan. */
    @Transactional
    public void setCommentHidden(UUID commentId, boolean hidden) {
        BlogComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "COMMENT_NOT_FOUND|Không tìm thấy bình luận."));
        comment.setStatus(hidden ? "HIDDEN" : STATUS_VISIBLE);
        commentRepository.save(comment);
    }

    /** Xoa han 1 binh luan (soft delete). */
    @Transactional
    public void deleteComment(UUID commentId) {
        BlogComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "COMMENT_NOT_FOUND|Không tìm thấy bình luận."));
        commentRepository.delete(comment); // @SQLDelete -> deleted_at
    }

    // ===================== Helpers =====================

    private void requireActiveCustomer(User user) {
        if (!user.isEmailVerified() || user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE|Tài khoản cần được kích hoạt và xác thực email để tham gia.");
        }
    }

    private String requireContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CONTENT|Nội dung không được để trống.");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CONTENT|Nội dung tối đa 1000 ký tự.");
        }
        return trimmed;
    }

    private BlogPostResponse toResponse(BlogPost post, User author, List<String> photoUrls, long commentCount) {
        // author co the null neu tac gia da bi soft-delete → hien ten trung tinh, khong lo PII (HR-17)
        String name = author != null ? author.getFullName() : "Người dùng Move_home";
        String avatar = author != null ? author.getAvatarUrl() : null;
        Integer rating = post.getRating() == null ? null : post.getRating().intValue();
        return new BlogPostResponse(
                post.getId(), name, avatar, post.getContent(), rating, photoUrls, commentCount, post.getCreatedAt());
    }

    private BlogCommentResponse toCommentResponse(BlogComment comment, User author) {
        String name = author != null ? author.getFullName() : "Người dùng Move_home";
        String avatar = author != null ? author.getAvatarUrl() : null;
        return new BlogCommentResponse(
                comment.getId(), name, avatar, comment.getAuthorRole(), comment.getContent(), comment.getCreatedAt());
    }
}
