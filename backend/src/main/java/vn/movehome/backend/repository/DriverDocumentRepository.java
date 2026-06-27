package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.movehome.backend.entity.DriverDocument;

import java.util.List;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    interface DocTypeCount {
        String getDocType();

        Long getCount();
    }

    List<DriverDocument> findByDriverIdOrderByUploadedAtDesc(UUID driverId);

    long countByDriverId(UUID driverId);

    @org.springframework.data.jpa.repository.Query("""
            select d.docType as docType, count(d) as count
            from DriverDocument d
            where d.driverId = :driverId
            group by d.docType
            """)
    List<DocTypeCount> countDocumentsByType(
            @org.springframework.data.repository.query.Param("driverId") UUID driverId);
}
