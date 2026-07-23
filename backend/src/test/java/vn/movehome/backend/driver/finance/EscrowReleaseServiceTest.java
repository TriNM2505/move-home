package vn.movehome.backend.driver.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Escrow 2h (CONTEXT muc 2): job dinh ky release thu nhap tai xe cho don COMPLETED da qua han giu.
 * Dung TransactionTemplate that (khong mock) nhung PlatformTransactionManager duoc mock -
 * moi lan getTransaction() tra ve 1 TransactionStatus gia, commit/rollback la no-op tren mock.
 */
@ExtendWith(MockitoExtension.class)
class EscrowReleaseServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DriverEarningService driverEarningService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private EscrowReleaseService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        service = new EscrowReleaseService(orderRepository, driverEarningService, transactionManager, 120L);
    }

    @Test
    void releaseDueEarningsDoesNothingWhenNoOrdersDue() {
        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of());

        service.releaseDueEarnings();

        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(driverEarningService, never()).creditEarning(any());
    }

    @Test
    void releaseDueEarningsLogsWarningAndReturnsWhenQueryFails() {
        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenThrow(new RuntimeException("db down"));

        service.releaseDueEarnings();

        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(driverEarningService, never()).creditEarning(any());
    }

    @Test
    void releaseDueEarningsCreditsEarningAndMarksOrderReleasedWhenStillCompleted() {
        UUID orderId = UUID.randomUUID();
        ServiceOrder listedOrder = ServiceOrder.builder().id(orderId).status("COMPLETED").build();
        ServiceOrder lockedOrder = ServiceOrder.builder()
                .id(orderId)
                .status("COMPLETED")
                .earningReleasedAt(null)
                .deletedAt(null)
                .build();

        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of(listedOrder));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(lockedOrder));

        service.releaseDueEarnings();

        verify(driverEarningService, times(1)).creditEarning(lockedOrder);
        ArgumentCaptor<ServiceOrder> savedCaptor = ArgumentCaptor.forClass(ServiceOrder.class);
        verify(orderRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getEarningReleasedAt()).isNotNull();
    }

    @Test
    void releaseDueEarningsSkipsOrderWhenLockedRowNoLongerExists() {
        UUID orderId = UUID.randomUUID();
        ServiceOrder listedOrder = ServiceOrder.builder().id(orderId).status("COMPLETED").build();

        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of(listedOrder));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        service.releaseDueEarnings();

        verify(driverEarningService, never()).creditEarning(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void releaseDueEarningsSkipsOrderWhenSoftDeleted() {
        UUID orderId = UUID.randomUUID();
        ServiceOrder listedOrder = ServiceOrder.builder().id(orderId).status("COMPLETED").build();
        ServiceOrder lockedOrder = ServiceOrder.builder()
                .id(orderId)
                .status("COMPLETED")
                .deletedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of(listedOrder));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(lockedOrder));

        service.releaseDueEarnings();

        verify(driverEarningService, never()).creditEarning(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void releaseDueEarningsSkipsOrderWhenStatusChangedAwayFromCompleted() {
        UUID orderId = UUID.randomUUID();
        ServiceOrder listedOrder = ServiceOrder.builder().id(orderId).status("COMPLETED").build();
        // Race voi dispute: don da chuyen sang DISPUTED truoc khi job chay toi
        ServiceOrder lockedOrder = ServiceOrder.builder().id(orderId).status("DISPUTED").build();

        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of(listedOrder));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(lockedOrder));

        service.releaseDueEarnings();

        verify(driverEarningService, never()).creditEarning(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void releaseDueEarningsSkipsOrderAlreadyReleased() {
        UUID orderId = UUID.randomUUID();
        ServiceOrder listedOrder = ServiceOrder.builder().id(orderId).status("COMPLETED").build();
        ServiceOrder lockedOrder = ServiceOrder.builder()
                .id(orderId)
                .status("COMPLETED")
                .earningReleasedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of(listedOrder));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(lockedOrder));

        service.releaseDueEarnings();

        verify(driverEarningService, never()).creditEarning(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void releaseDueEarningsContinuesWithNextOrderWhenOneOrderFails() {
        UUID failingOrderId = UUID.randomUUID();
        UUID okOrderId = UUID.randomUUID();
        ServiceOrder failingListed = ServiceOrder.builder().id(failingOrderId).status("COMPLETED").build();
        ServiceOrder okListed = ServiceOrder.builder().id(okOrderId).status("COMPLETED").build();
        ServiceOrder failingLocked = ServiceOrder.builder().id(failingOrderId).status("COMPLETED").build();
        ServiceOrder okLocked = ServiceOrder.builder().id(okOrderId).status("COMPLETED").build();

        when(orderRepository.findByStatusAndEarningReleasedAtIsNullAndCompletedAtBeforeAndDeletedAtIsNull(
                eq("COMPLETED"), any())).thenReturn(List.of(failingListed, okListed));
        when(orderRepository.findByIdForUpdate(failingOrderId)).thenReturn(Optional.of(failingLocked));
        when(orderRepository.findByIdForUpdate(okOrderId)).thenReturn(Optional.of(okLocked));
        doThrow(new RuntimeException("wallet locked")).when(driverEarningService).creditEarning(failingLocked);

        service.releaseDueEarnings();

        verify(driverEarningService).creditEarning(failingLocked);
        verify(driverEarningService).creditEarning(okLocked);
        verify(orderRepository, never()).save(failingLocked);
        verify(orderRepository).save(okLocked);
    }
}
