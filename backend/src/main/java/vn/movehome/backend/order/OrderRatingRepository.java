package vn.movehome.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRatingRepository extends JpaRepository<OrderRating, UUID> {

    interface RatingStarCount {
        Integer getStar();

        Long getCount();
    }

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

    @Query("""
            select r.stars as star, count(r) as count
            from OrderRating r
            where r.driverId = :driverId
            group by r.stars
            """)
    List<RatingStarCount> countRatingsByDriverGroupByStar(@Param("driverId") UUID driverId);

    @Query("select avg(r.stars) from OrderRating r where r.driverId = :driverId")
    Optional<BigDecimal> averageRatingByDriver(@Param("driverId") UUID driverId);
}
