package vn.movehome.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderCancellationPhotoRepository extends JpaRepository<OrderCancellationPhoto, UUID> {

    List<OrderCancellationPhoto> findByCancellationIdOrderByUploadedAtAsc(UUID cancellationId);

    long countByCancellationId(UUID cancellationId);
}
