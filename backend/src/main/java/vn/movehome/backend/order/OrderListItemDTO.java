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
public class OrderListItemDTO {

    private UUID id;
    private String orderCode;
    private String status;
    private BigDecimal totalQuote;
    private String pickupDistrict;
    private String dropoffDistrict;
    private OffsetDateTime createdAt;
}
