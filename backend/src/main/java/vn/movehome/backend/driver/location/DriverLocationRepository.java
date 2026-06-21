package vn.movehome.backend.driver.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface DriverLocationRepository extends JpaRepository<DriverLocation, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO driver_location (
                driver_id, current_order_id, lat, lng, heading, speed_kmh, recorded_at, updated_at
            ) VALUES (
                :driverId, :currentOrderId, :lat, :lng, :heading, :speedKmh, NOW(), NOW()
            )
            ON CONFLICT (driver_id) DO UPDATE SET
                current_order_id = EXCLUDED.current_order_id,
                lat = EXCLUDED.lat,
                lng = EXCLUDED.lng,
                heading = EXCLUDED.heading,
                speed_kmh = EXCLUDED.speed_kmh,
                recorded_at = EXCLUDED.recorded_at,
                updated_at = EXCLUDED.updated_at
            WHERE driver_location.recorded_at <= NOW() - INTERVAL '10 seconds'
            """, nativeQuery = true)
    int upsertLatest(
            @Param("driverId") UUID driverId,
            @Param("currentOrderId") UUID currentOrderId,
            @Param("lat") BigDecimal lat,
            @Param("lng") BigDecimal lng,
            @Param("heading") BigDecimal heading,
            @Param("speedKmh") BigDecimal speedKmh);
}
