package vn.movehome.backend.blog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Truy van binh luan Blog cong dong. Query mac dinh da loc deleted_at IS NULL (@SQLRestriction).
 */
public interface BlogCommentRepository extends JpaRepository<BlogComment, UUID> {

    // Binh luan VISIBLE cua 1 bai (cu -> moi).
    @Query("SELECT c FROM BlogComment c WHERE c.postId = :postId AND c.status = 'VISIBLE' "
            + "ORDER BY c.createdAt ASC, c.id ASC")
    List<BlogComment> findVisibleByPost(UUID postId);

    // Dem binh luan VISIBLE cho ca 1 trang feed (tranh N+1). Moi phan tu: [postId (UUID), count (Long)].
    @Query("SELECT c.postId, COUNT(c) FROM BlogComment c "
            + "WHERE c.postId IN :postIds AND c.status = 'VISIBLE' GROUP BY c.postId")
    List<Object[]> countVisibleByPostIds(Collection<UUID> postIds);
}
