package vn.movehome.backend.dispute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Job quet cac khoan phat qua han nop bo sung (2 phut demo):
 * khoa tai khoan tai xe + tru coc boi thuong khach (DisputeService.enforcePenalty).
 * Chay 15s/lan de deadline 2 phut phan hoi nhanh khi demo.
 * Moi dispute xu ly trong transaction rieng — 1 cai loi khong chan cac cai khac.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PenaltyEnforcementScheduler {

    private final DisputeService disputeService;

    @Scheduled(fixedRate = 15_000L)
    public void enforceExpiredPenalties() {
        List<UUID> dueIds;
        try {
            dueIds = disputeService.findExpiredPenaltyIds();
        } catch (RuntimeException ex) {
            log.warn("Khong the quet khoan phat qua han: {}", ex.getMessage());
            return;
        }
        for (UUID disputeId : dueIds) {
            try {
                disputeService.enforcePenalty(disputeId);
                log.info("penalty_enforced dispute_id={}", disputeId);
            } catch (RuntimeException ex) {
                log.error("Loi xu ly khoan phat qua han dispute_id={}: {}", disputeId, ex.getMessage(), ex);
            }
        }
    }
}
