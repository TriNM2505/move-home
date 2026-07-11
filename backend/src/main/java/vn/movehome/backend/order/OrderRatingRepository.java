package vn.movehome.backend.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.movehome.backend.dto.manager.DriverRatingItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    @Query("""
            select r.stars as star, count(r) as count
            from OrderRating r
            where r.createdAt >= :from and r.createdAt < :to
            group by r.stars
            """)
    List<RatingStarCount> countRatingsByStarBetween(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("select avg(r.stars) from OrderRating r where r.createdAt >= :from and r.createdAt < :to")
    Optional<BigDecimal> averageRatingBetween(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Danh sach danh gia cho man Manager "Đánh giá tài xế" (kem comment).
     * Filter tuy chon: theo tai xe, theo so sao, theo ten tai xe.
     * left join tai xe vi driver_id co the NULL voi du lieu cu (V9).
     * LUU Y (PostgreSQL + param nullable):
     * - Filter nhan STRING (service convert UUID/Integer → String) — tranh loi
     *   "could not determine data type of parameter" khi bind null.
     * - keywordPattern phai duoc service LOWERCASE san dang "%tu khoa%" — KHONG boc lower(:param)
     *   trong query vi bind null vao lower(?) → PostgreSQL suy nhầm kieu bytea → "function
     *   lower(bytea) does not exist" (loi 500 thuc te da gap 2026-07-11).
     */
    @Query(value = """
            select new vn.movehome.backend.dto.manager.DriverRatingItem(
                r.id, r.orderId, o.orderCode,
                r.driverId, d.fullName,
                c.fullName,
                r.stars, r.comment, r.createdAt)
            from OrderRating r
            join CustomerServiceOrder o on o.id = r.orderId
            join User c on c.id = r.customerId
            left join User d on d.id = r.driverId
            where (:driverId is null or cast(r.driverId as string) = :driverId)
              and (:stars is null or cast(r.stars as string) = :stars)
              and (:keywordPattern is null
                   or lower(coalesce(d.fullName, '')) like :keywordPattern)
            order by r.createdAt desc
            """,
            countQuery = """
            select count(r)
            from OrderRating r
            left join User d on d.id = r.driverId
            where (:driverId is null or cast(r.driverId as string) = :driverId)
              and (:stars is null or cast(r.stars as string) = :stars)
              and (:keywordPattern is null
                   or lower(coalesce(d.fullName, '')) like :keywordPattern)
            """)
    Page<DriverRatingItem> searchForManager(
            @Param("driverId") String driverId,
            @Param("stars") String stars,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);
}
