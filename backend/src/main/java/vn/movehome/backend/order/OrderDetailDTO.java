package vn.movehome.backend.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailDTO {

    private UUID id;
    private String orderCode;
    private String status;
    private String vehicleType;
    private String pickupAddress;
    private String pickupDistrict;
    private String dropoffAddress;
    private String dropoffDistrict;
    private OffsetDateTime scheduledAt;
    private BigDecimal distanceKm;
    private BigDecimal baseFare;
    private BigDecimal peakSurcharge;
    private BigDecimal alleySurcharge;
    private BigDecimal floorSurcharge;
    private BigDecimal porterFee;
    private BigDecimal totalQuote;
    private Integer porterCount;
    private String notes;
    private UUID driverId;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime cancelledAt;
}
