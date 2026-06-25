package vn.movehome.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commission_settings")
@Getter
@Setter
public class CommissionSettings {

    @Id
    private Integer id;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(nullable = false)
    private Long version;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;

    @Column(name = "last_updated_by")
    private UUID lastUpdatedBy;
}
