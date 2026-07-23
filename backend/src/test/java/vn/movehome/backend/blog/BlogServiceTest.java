package vn.movehome.backend.blog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    private User driver() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("driver@movehome.vn")
                .fullName("Tai Xe")
                .role(UserRole.DRIVER)
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

    // ---------- createPost: them nhanh cac nhanh con lai ----------

    @Test
    void createPost_noiDungQuaDai_tra422() {
        User author = activeCustomer();
        String tooLong = "a".repeat(1001);

        assertThatThrownBy(() -> service.createPost(author, tooLong, null, List.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("INVALID_CONTENT|Nội dung tối đa 1000 ký tự.");
                });

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_ratingDuoi1_tra422() {
        User author = activeCustomer();

        assertThatThrownBy(() -> service.createPost(author, "Nội dung hợp lệ", 0, List.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_khongXacThucEmail_tra403() {
        User author = User.builder()
                .id(UUID.randomUUID())
                .fullName("Chua xac thuc")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        assertThatThrownBy(() -> service.createPost(author, "Nội dung", null, List.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_ratingNull_vaCoAnh_traVeDanhSachUrl() {
        User author = activeCustomer();
        when(postRepository.save(any(BlogPost.class))).thenAnswer(inv -> {
            BlogPost p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        BlogPostPhoto photo = BlogPostPhoto.builder()
                .id(UUID.randomUUID()).postId(UUID.randomUUID()).url("https://cdn/1.jpg")
                .publicId("pid").uploadedByUserId(author.getId()).build();
        when(photoService.attachPhotos(any(), eq(author.getId()), any())).thenReturn(List.of(photo));

        BlogPostResponse res = service.createPost(author, "Nội dung có ảnh", null, List.of());

        assertThat(res.rating()).isNull();
        assertThat(res.photos()).containsExactly("https://cdn/1.jpg");
    }

    // ---------- feed() ----------

    @Test
    void feed_khongCoBai_traVePageRong() {
        Page<BlogPost> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(postRepository.findVisible(any())).thenReturn(emptyPage);

        Page<BlogPostResponse> res = service.feed(0, 10);

        assertThat(res.getContent()).isEmpty();
        assertThat(res.getTotalElements()).isZero();
    }

    @Test
    void feed_coBai_ghepTacGiaAnhVaSoBinhLuan_taGiaBiXoaMemTraTenTrungTinh() {
        User author = activeCustomer();
        UUID postWithAuthor = UUID.randomUUID();
        UUID postAuthorDeleted = UUID.randomUUID();
        BlogPost p1 = BlogPost.builder().id(postWithAuthor).authorId(author.getId())
                .content("bai 1").rating((short) 4).status("VISIBLE").build();
        BlogPost p2 = BlogPost.builder().id(postAuthorDeleted).authorId(UUID.randomUUID())
                .content("bai 2").status("VISIBLE").build();
        // page size vuot MAX_PAGE_SIZE (50) va page am -> phai duoc "kep" ve gia tri hop le
        Page<BlogPost> page = new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 50), 2);
        when(postRepository.findVisible(any())).thenReturn(page);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(author));
        BlogPostPhoto photo = BlogPostPhoto.builder()
                .id(UUID.randomUUID()).postId(postWithAuthor).url("https://cdn/a.jpg")
                .publicId("pid").build();
        when(photoRepository.findByPostIdInOrderByUploadedAtAsc(anyCollection())).thenReturn(List.of(photo));
        when(commentRepository.countVisibleByPostIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{postWithAuthor, 3L}));

        Page<BlogPostResponse> res = service.feed(-1, 999);

        assertThat(res.getContent()).hasSize(2);
        BlogPostResponse res1 = res.getContent().stream().filter(r -> r.id().equals(postWithAuthor)).findFirst().orElseThrow();
        assertThat(res1.authorName()).isEqualTo("Nguyen Van A");
        assertThat(res1.photos()).containsExactly("https://cdn/a.jpg");
        assertThat(res1.commentCount()).isEqualTo(3L);
        assertThat(res1.rating()).isEqualTo(4);

        BlogPostResponse res2 = res.getContent().stream().filter(r -> r.id().equals(postAuthorDeleted)).findFirst().orElseThrow();
        assertThat(res2.authorName()).isEqualTo("Người dùng Move_home");
        assertThat(res2.photos()).isEmpty();
        assertThat(res2.commentCount()).isZero();
    }

    // ---------- addComment(): cac nhanh con lai ----------

    @Test
    void addComment_khongPhaiCustomerHayManager_tra403() {
        User d = driver();
        UUID postId = UUID.randomUUID();

        assertThatThrownBy(() -> service.addComment(d, postId, "Nội dung"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getReason()).isEqualTo("FORBIDDEN|Chỉ khách hàng hoặc quản lý mới được bình luận.");
                });

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_customerChuaKichHoat_tra403() {
        User author = activeCustomer();
        author.setStatus(UserStatus.SUSPENDED);
        UUID postId = UUID.randomUUID();

        assertThatThrownBy(() -> service.addComment(author, postId, "Nội dung"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(postRepository, never()).findById(any());
    }

    @Test
    void addComment_baiDaBiAn_tra404() {
        User author = activeCustomer();
        UUID postId = UUID.randomUUID();
        BlogPost hidden = BlogPost.builder()
                .id(postId).authorId(UUID.randomUUID()).content("x").status("HIDDEN").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.addComment(author, postId, "Nội dung"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_chuBaiTuBinhLuanBaiCuaMinh_khongGuiThongBao() {
        User author = activeCustomer();
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder()
                .id(postId).authorId(author.getId()).content("bài").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(BlogComment.class))).thenAnswer(inv -> {
            BlogComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        service.addComment(author, postId, "Tự bình luận bài của mình");

        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void addComment_loiTaoThongBao_khongLamHongBinhLuan() {
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
        doThrow(new RuntimeException("smtp down"))
                .when(notificationService).create(any(), any(), any(), any());

        BlogCommentResponse res = service.addComment(commenter, postId, "Nội dung dài hơn 80 ký tự "
                + "để kiểm tra logic cắt chuỗi snippet khi tạo thông báo cho chủ bài viết trên blog cộng đồng nhé");

        assertThat(res).isNotNull();
    }

    // ---------- listComments() ----------

    @Test
    void listComments_baiKhongTonTai_tra404() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listComments(postId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listComments_khongCoBinhLuan_traVeDanhSachRong() {
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder().id(postId).authorId(UUID.randomUUID()).content("x").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findVisibleByPost(postId)).thenReturn(List.of());

        List<BlogCommentResponse> res = service.listComments(postId);

        assertThat(res).isEmpty();
    }

    @Test
    void listComments_coBinhLuan_taGiaBiXoaMemTraTenTrungTinh() {
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder().id(postId).authorId(UUID.randomUUID()).content("x").status("VISIBLE").build();
        User author = activeCustomer();
        BlogComment c1 = BlogComment.builder().id(UUID.randomUUID()).postId(postId)
                .authorId(author.getId()).authorRole("CUSTOMER").content("bl 1").status("VISIBLE").build();
        BlogComment c2 = BlogComment.builder().id(UUID.randomUUID()).postId(postId)
                .authorId(UUID.randomUUID()).authorRole("CUSTOMER").content("bl 2").status("VISIBLE").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findVisibleByPost(postId)).thenReturn(List.of(c1, c2));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(author));

        List<BlogCommentResponse> res = service.listComments(postId);

        assertThat(res).hasSize(2);
        assertThat(res.stream().filter(r -> r.authorName().equals("Nguyen Van A")).count()).isEqualTo(1);
        assertThat(res.stream().filter(r -> r.authorName().equals("Người dùng Move_home")).count()).isEqualTo(1);
    }

    // ---------- Pha C: cac nhanh con lai ----------

    @Test
    void setPostHidden_khongTonTai_tra404() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPostHidden(postId, true))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setPostHidden_boAn_datLaiVisible() {
        UUID postId = UUID.randomUUID();
        BlogPost post = BlogPost.builder().id(postId).authorId(UUID.randomUUID()).content("x").status("HIDDEN").build();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service.setPostHidden(postId, false);

        assertThat(post.getStatus()).isEqualTo("VISIBLE");
        verify(postRepository).save(post);
    }

    @Test
    void deletePost_khongTonTai_tra404() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(postId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(photoService, never()).deletePhotosByPost(any());
    }

    @Test
    void setCommentHidden_datHidden() {
        UUID commentId = UUID.randomUUID();
        BlogComment comment = BlogComment.builder().id(commentId).postId(UUID.randomUUID())
                .authorId(UUID.randomUUID()).authorRole("CUSTOMER").content("x").status("VISIBLE").build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        service.setCommentHidden(commentId, true);

        assertThat(comment.getStatus()).isEqualTo("HIDDEN");
        verify(commentRepository).save(comment);
    }

    @Test
    void setCommentHidden_boAn_datLaiVisible() {
        UUID commentId = UUID.randomUUID();
        BlogComment comment = BlogComment.builder().id(commentId).postId(UUID.randomUUID())
                .authorId(UUID.randomUUID()).authorRole("CUSTOMER").content("x").status("HIDDEN").build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        service.setCommentHidden(commentId, false);

        assertThat(comment.getStatus()).isEqualTo("VISIBLE");
    }

    @Test
    void setCommentHidden_khongTonTai_tra404() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCommentHidden(commentId, true))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteComment_xoaThanhCong() {
        UUID commentId = UUID.randomUUID();
        BlogComment comment = BlogComment.builder().id(commentId).postId(UUID.randomUUID())
                .authorId(UUID.randomUUID()).authorRole("CUSTOMER").content("x").status("VISIBLE").build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        service.deleteComment(commentId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_khongTonTai_tra404() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(commentId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(commentRepository, never()).delete(any(BlogComment.class));
    }
}
