package vn.movehome.backend.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputePhotoRepository extends JpaRepository<DisputePhoto, UUID> {

    List<DisputePhoto> findByDisputeIdOrderByUploadedAtAsc(UUID disputeId);

    long countByDisputeId(UUID disputeId);
}
