package vn.movehome.backend.blog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Truy van anh cua bai dang cong dong.
 */
public interface BlogPostPhotoRepository extends JpaRepository<BlogPostPhoto, UUID> {

    // Batch load anh cho ca 1 trang feed (tranh N+1).
    List<BlogPostPhoto> findByPostIdInOrderByUploadedAtAsc(Collection<UUID> postIds);
}
