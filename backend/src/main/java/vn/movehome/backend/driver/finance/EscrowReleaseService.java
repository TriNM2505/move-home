package vn.movehome.backend.driver.finance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Escrow 2h (CONTEXT §2): sau khi don COMPLETED, giu 70% cua tai xe trong thoi gian escrow.
 * Het thoi gian ma khach khong khieu nai (don van COMPLETED, chua release) → cong tien vao vi tai xe.
 * Thoi gian giu cau hinh qua app.escrow.hold-minutes (mac dinh 120 phut = 2 gio; ha xuong de demo).
 */
@Service
@Slf4j
public class EscrowReleaseService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    private final OrderRepository orderRepository;
    private final DriverEarningService driverEarningService;
    private final TransactionTemplate transactionTemplate;
    private final long holdMinutes;

    public EscrowReleaseService(
            OrderRepository orderRepository,
            DriverEarningService driverEarningService,
            PlatformTransactionManager transactionManager,
            @Value("${app.escrow.hold-minutes:120}") long holdMinutes) {
        this.orderRepository = orderRepository;
        this.driverEarningService = driverEarningService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.holdMinutes = holdMinutes;
    }

    /** Moi 60s: giai phong thu nhap cac don COMPLETED da qua thoi gian escrow, khong bi khieu nai. */
    @Scheduled(fixedRate = 60_000L)
    public void releaseDueEarnings() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(holdMinutes);
        List<ServiceOrder> due;
        try {
            due = orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                    COMPLETED_STATUS, cutoff);
        } catch (RuntimeException ex) {
            log.warn("Escrow: khong tai duoc danh sach don den han release.", ex);
            return;
        }

        int released = 0;
        for (ServiceOrder order : due) {
            try {
                // Moi don 1 transaction rieng (REQUIRES_NEW) → 1 don loi khong anh huong don khac
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> releaseOne(order.getId())))) {
                    released++;
                }
            } catch (RuntimeException ex) {
                log.error("Escrow: loi khi release don {}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
        if (released > 0) {
            log.info("Escrow: da giai phong thu nhap cho {} don.", released);
        }
    }

    private boolean releaseOne(UUID orderId) {
        ServiceOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(item -> item.getDeletedAt() == null)
                .orElse(null);
        if (order == null) {
            return false;
        }
        // Double-check trong transaction: van COMPLETED va chua release (tranh race voi dispute)
        if (!COMPLETED_STATUS.equals(order.getStatus()) || order.getEarningReleasedAt() != null) {
            return false;
        }
        driverEarningService.creditEarning(order);
        order.setEarningReleasedAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(order);
        return true;
    }
}
