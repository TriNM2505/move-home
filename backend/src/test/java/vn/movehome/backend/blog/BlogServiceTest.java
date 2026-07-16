package vn.movehome.backend.blog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.blog.dto.BlogCommentResponse;
import vn.movehome.backend.blog.dto.BlogPostResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test BlogService — dang bai (Pha A) + binh luan/Manager tra loi (Pha B).
 */
@ExtendWith(MockitoExtension.class)
class BlogServiceTest {

    @Mock
    private BlogPostRepository postRepository;
    @Mock
    private BlogPostPhotoRepository photoRepository;
    @Mock
    private BlogCommentRepository commentRepository;
    @Mock
    private BlogPhotoService photoService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BlogRateLimiter rateLimiter;

    private BlogService service;

    @BeforeEach
    void setUp() {
        service = new BlogService(postRepository, photoRepository, commentRepository,
                photoService, userRepository, notificationService, rateLimiter);
    }

    private User activeCustomer() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("khach@example.com")
                .fullName("Nguyen Van A")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }

    private User manager() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("ql@movehome.vn")
                .fullName("Quan Ly")
                .role(UserRole.MANAGER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }

    // ---------- Pha A: dang bai ----------

    @Test
    void createPost_happyPath_luuBaiVaTraVeResponse() {
        User author = activeCustomer();
        when(postRepository.save(any(BlogPost.class))).thenAnswer(inv -> {
            BlogPost p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(photoService.attachPhotos(any(), eq(author.getId()), any())).thenReturn(List.of());

        BlogPostResponse res = service.createPost(author, "  Dịch vụ rất tốt  ", 5, List.of());

        assertThat(res.content()).isEqualTo("Dịch vụ rất tốt"); // da trim
        assertThat(res.rating()).isEqualTo(5);
        assertThat(res.authorName()).isEqualTo("Nguyen Van A");
        assertThat(res.photos()).isEmpty();
        assertThat(res.commentCount()).isZero();
        verify(postRepository).save(any(BlogPost.class));
    }

    @Test
    void createPost_taiKhoanChuaKichHoat_tra403() {
        User author = activeCustomer();
        author.setStatus(UserStatus.PENDING_VERIFY);

        assertThatThrownBy(() -> service.createPost(author, "Nội dung", null, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_noiDungRong_tra422() {
        User author = activeCustomer();

        assertThatThrownBy(() -> service.createPost(author, "   ", null, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_ratingNgoaiKhoang_tra422() {
        User author = activeCustomer();

        assertThatThrownBy(() -> service.createPost(author, "Nội dung hợp lệ", 9, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(postRepository, never()).save(any());
    }

    // ---------- Pha B: binh luan ----------

    @Test
    void addComment_customerBinhLuanBaiNguoiKhac_luuVaBaoChuBai() {
        User commenter = activeCustomer();
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BlogPost post = BlogPost.builder()
                .id(postId).authorId(ownerId).content("bài").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(BlogComment.class))).thenAnswer(inv -> {
            BlogComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        BlogCommentResponse res = service.addComment(commenter, postId, "  Cảm ơn shop  ");

        assertThat(res.content()).isEqualTo("Cảm ơn shop");
        assertThat(res.authorRole()).isEqualTo("CUSTOMER");
        // Bao cho chu bai (khac nguoi binh luan)
        verify(notificationService).create(eq(ownerId), eq("BLOG_COMMENT"), any(), any());
    }

    @Test
    void addComment_managerTraLoi_snapshotRoleManager() {
        User mgr = manager();
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder()
                .id(postId).authorId(UUID.randomUUID()).content("bài").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(BlogComment.class))).thenAnswer(inv -> {
            BlogComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        BlogCommentResponse res = service.addComment(mgr, postId, "Cảm ơn bạn đã phản hồi");

        assertThat(res.authorRole()).isEqualTo("MANAGER");
        assertThat(res.authorName()).isEqualTo("Quan Ly");
        verify(notificationService).create(any(), eq("BLOG_COMMENT"), any(), any());
    }

    @Test
    void addComment_baiKhongTonTai_tra404() {
        User commenter = activeCustomer();
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComment(commenter, postId, "Nội dung"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(commentRepository, never()).save(any());
    }

    // ---------- Pha C: kiem duyet ----------

    @Test
    void setPostHidden_datStatusHidden() {
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder()
                .id(postId).authorId(UUID.randomUUID()).content("x").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service.setPostHidden(postId, true);

        assertThat(post.getStatus()).isEqualTo("HIDDEN");
        verify(postRepository).save(post);
    }

    @Test
    void deletePost_donAnhCloudinaryVaXoaBai() {
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder()
                .id(postId).authorId(UUID.randomUUID()).content("x").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service.deletePost(postId);

        verify(photoService).deletePhotosByPost(postId);
        verify(postRepository).delete(post);
    }
}
