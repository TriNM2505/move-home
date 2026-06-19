package vn.movehome.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRatingRepository extends JpaRepository<OrderRating, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<OrderRating> findByOrderId(UUID orderId);

    @Query(
            value = """
                    SELECT COALESCE(ROUND(AVG(stars)::numeric, 2), 0)
                    FROM order_rating
                    WHERE driver_id = :driverId
                    """,
            nativeQuery = true
    )
    BigDecimal calculateAverageStarsByDriverId(@Param("driverId") UUID driverId);
}
