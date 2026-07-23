package vn.movehome.backend.dispute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PenaltyEnforcementSchedulerTest {

    @Mock
    private DisputeService disputeService;

    private PenaltyEnforcementScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PenaltyEnforcementScheduler(disputeService);
    }

    @Test
    void enforceExpiredPenaltiesDoesNothingWhenNoDisputeIsDue() {
        when(disputeService.findExpiredPenaltyIds()).thenReturn(List.of());

        scheduler.enforceExpiredPenalties();

        verify(disputeService, never()).enforcePenalty(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enforceExpiredPenaltiesEnforcesEachDueDisputeInOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(disputeService.findExpiredPenaltyIds()).thenReturn(List.of(first, second));

        scheduler.enforceExpiredPenalties();

        InOrder order = inOrder(disputeService);
        order.verify(disputeService).enforcePenalty(first);
        order.verify(disputeService).enforcePenalty(second);
    }

    @Test
    void enforceExpiredPenaltiesStopsScanQuietlyWhenFindingDueIdsFails() {
        when(disputeService.findExpiredPenaltyIds()).thenThrow(new RuntimeException("DB down"));

        scheduler.enforceExpiredPenalties();

        verify(disputeService, never()).enforcePenalty(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enforceExpiredPenaltiesContinuesWithNextDisputeWhenOneEnforcementFails() {
        UUID failing = UUID.randomUUID();
        UUID succeeding = UUID.randomUUID();
        when(disputeService.findExpiredPenaltyIds()).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("boom")).when(disputeService).enforcePenalty(failing);

        scheduler.enforceExpiredPenalties();

        verify(disputeService).enforcePenalty(failing);
        verify(disputeService).enforcePenalty(succeeding);
    }
}
