package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.movehome.backend.entity.DriverDocument;

import java.util.List;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriverIdOrderByUploadedAtDesc(UUID driverId);

    long countByDriverId(UUID driverId);
}
