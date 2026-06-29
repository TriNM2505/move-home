package vn.movehome.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.movehome.backend.entity.LoginEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "INSERT INTO login_event (user_id) VALUES (:userId)", nativeQuery = true)
    void insertSuccessfulLogin(@Param("userId") UUID userId);

    @Query(value = """
            SELECT COALESCE(AVG(daily_count), 0)
            FROM (
                SELECT COUNT(DISTINCT le.user_id) AS daily_count
                FROM login_event le
                JOIN app_user u ON u.id = le.user_id
                WHERE le.logged_in_at >= :from AND le.logged_in_at < :to
                  AND u.role = 'CUSTOMER'
                  AND u.deleted_at IS NULL
                GROUP BY DATE(le.logged_in_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
            ) sub
            """, nativeQuery = true)
    BigDecimal calculateCustomerDauAverage(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query(value = """
            SELECT COUNT(DISTINCT le.user_id)
            FROM login_event le
            JOIN app_user u ON u.id = le.user_id
            WHERE le.logged_in_at >= :from AND le.logged_in_at < :to
              AND u.role = 'CUSTOMER'
              AND u.deleted_at IS NULL
            """, nativeQuery = true)
    long countCustomerMauBetween(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query(value = """
            WITH eligible AS (
                SELECT DISTINCT le.user_id
                FROM login_event le
                JOIN app_user u ON u.id = le.user_id
                WHERE le.logged_in_at >= :cohortStart AND le.logged_in_at < :periodStart
                  AND u.role = 'CUSTOMER'
                  AND u.deleted_at IS NULL
            )
            SELECT
                COUNT(DISTINCT e.user_id) FILTER (
                    WHERE EXISTS (
                        SELECT 1
                        FROM login_event retained
                        WHERE retained.user_id = e.user_id
                          AND retained.logged_in_at >= :periodStart
                          AND retained.logged_in_at < :periodEnd
                    )
                )::decimal / NULLIF(COUNT(DISTINCT e.user_id), 0) AS rate
            FROM eligible e
            """, nativeQuery = true)
    BigDecimal calculateCustomerRetentionRate30d(
            @Param("cohortStart") OffsetDateTime cohortStart,
            @Param("periodStart") OffsetDateTime periodStart,
            @Param("periodEnd") OffsetDateTime periodEnd);
}

