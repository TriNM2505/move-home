package vn.movehome.backend.blog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * Truy van bai dang cong dong. Query mac dinh da loc deleted_at IS NULL (@SQLRestriction tren entity).
 */
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    // Feed cong khai: chi bai VISIBLE, moi nhat truoc. Server-side pagination (AC-15).
    @Query("SELECT p FROM BlogPost p WHERE p.status = 'VISIBLE' ORDER BY p.createdAt DESC, p.id DESC")
    Page<BlogPost> findVisible(Pageable pageable);
}
