package vn.movehome.backend.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriverIncidentPhotoRepository extends JpaRepository<DriverIncidentPhoto, UUID> {

    List<DriverIncidentPhoto> findByIncidentIdOrderByUploadedAtAsc(UUID incidentId);

    long countByIncidentId(UUID incidentId);
}
