package vn.movehome.backend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import vn.movehome.backend.driver.DriverOrderService;
import vn.movehome.backend.driver.finance.DriverEarningService;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;
import vn.movehome.backend.repository.DriverProfileRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(OrderStatusEventTransactionTest.TestConfig.class)
class OrderStatusEventTransactionTest {

    private static final String ORDER_CODE = "MH202606210001";

    @jakarta.annotation.Resource
    private DriverOrderService driverOrderService;

    @jakarta.annotation.Resource
    private CustomerOrderActionService customerOrderActionService;

    @jakarta.annotation.Resource
    private OrderRepository orderRepository;

    @jakarta.annotation.Resource
    private DriverEarningService driverEarningService;

    @jakarta.annotation.Resource
    private AfterCommitEventCollector eventCollector;

    @BeforeEach
    void setUp() {
        reset(orderRepository, driverEarningService);
        eventCollector.clear();
        when(orderRepository.save(any(ServiceOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptPublishesOneCompleteEventAfterCommit() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, null, "PENDING");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        driverOrderService.acceptOrder(driverId, "DRIVER", orderId);

        assertEvent(orderId, customerId, driverId, "PENDING", "ACCEPTED", driverId, "DRIVER");
        verify(orderRepository).save(order);
    }

    @Test
    void startPublishesOneCompleteEventAfterCommit() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, driverId, "ACCEPTED");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        driverOrderService.startOrder(driverId, "DRIVER", orderId);

        assertThat(order.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(order.getStartedAt()).isNotNull();
        assertEvent(orderId, customerId, driverId, "ACCEPTED", "IN_PROGRESS", driverId, "DRIVER");
        assertThat(eventCollector.events())
                .singleElement()
                .satisfies(event -> assertThat(order.getStartedAt()).isEqualTo(event.changedAt()));
        verify(orderRepository).save(order);
    }

    @Test
    void completePublishesOnlyAfterOuterTransactionCommits() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, driverId, "IN_PROGRESS");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        doAnswer(invocation -> {
            assertThat(eventCollector.events()).isEmpty();
            return null;
        }).when(driverEarningService).creditEarning(order);

        driverOrderService.completeOrder(driverId, "DRIVER", orderId);

        assertThat(order.getCompletedAt()).isNotNull();
        assertEvent(orderId, customerId, driverId, "IN_PROGRESS", "COMPLETED", driverId, "DRIVER");
        verify(orderRepository).save(order);
        verify(driverEarningService).creditEarning(order);
    }

    @Test
    void cancelPublishesCustomerAsActualActorAfterCommit() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, driverId, "PENDING");
        when(orderRepository.findByIdAndCustomerIdForUpdate(orderId, customerId))
                .thenReturn(Optional.of(order));

        CancelOrderResponse response = customerOrderActionService.cancelOrder(
                customerId, "CUSTOMER", orderId, new CancelOrderRequest("  Changed plans  "));

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getCancellationReason()).isEqualTo("Changed plans");
        assertEvent(orderId, customerId, driverId, "PENDING", "CANCELLED", customerId, "CUSTOMER");
        verify(orderRepository).save(order);
    }

    @Test
    void rolledBackCompletionDoesNotReachAfterCommitListener() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        ServiceOrder order = order(orderId, customerId, driverId, "IN_PROGRESS");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("credit failed"))
                .when(driverEarningService).creditEarning(order);

        assertThatThrownBy(() -> driverOrderService.completeOrder(driverId, "DRIVER", orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("credit failed");

        assertThat(eventCollector.events()).isEmpty();
    }

    private ServiceOrder order(UUID orderId, UUID customerId, UUID driverId, String status) {
        return ServiceOrder.builder()
                .id(orderId)
                .orderCode(ORDER_CODE)
                .customerId(customerId)
                .driverId(driverId)
                .status(status)
                .build();
    }

    private void assertEvent(
            UUID orderId,
            UUID customerId,
            UUID driverId,
            String oldStatus,
            String newStatus,
            UUID changedByUserId,
            String changedByRole
    ) {
        assertThat(eventCollector.events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.orderId()).isEqualTo(orderId);
                    assertThat(event.orderCode()).isEqualTo(ORDER_CODE);
                    assertThat(event.oldStatus()).isEqualTo(oldStatus);
                    assertThat(event.newStatus()).isEqualTo(newStatus);
                    assertThat(event.customerId()).isEqualTo(customerId);
                    assertThat(event.driverId()).isEqualTo(driverId);
                    assertThat(event.changedByUserId()).isEqualTo(changedByUserId);
                    assertThat(event.changedByRole()).isEqualTo(changedByRole);
                    assertThat(event.changedAt()).isNotNull();
                });
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new InMemoryTransactionManager();
        }

        @Bean
        OrderRepository orderRepository() {
            return mock(OrderRepository.class);
        }

        @Bean
        DriverEarningService driverEarningService() {
            return mock(DriverEarningService.class);
        }

        @Bean
        OrderRatingRepository orderRatingRepository() {
            return mock(OrderRatingRepository.class);
        }

        @Bean
        DriverProfileRepository driverProfileRepository() {
            return mock(DriverProfileRepository.class);
        }

        @Bean
        OrderStatusTransitionService orderStatusTransitionService(
                OrderRepository orderRepository,
                ApplicationEventPublisher eventPublisher
        ) {
            return new OrderStatusTransitionService(orderRepository, eventPublisher);
        }

        @Bean
        DriverOrderService driverOrderService(
                OrderRepository orderRepository,
                OrderStatusTransitionService transitionService,
                DriverEarningService driverEarningService
        ) {
            return new DriverOrderService(orderRepository, transitionService, driverEarningService);
        }

        @Bean
        CustomerOrderActionService customerOrderActionService(
                OrderRepository orderRepository,
                OrderStatusTransitionService transitionService,
                OrderRatingRepository orderRatingRepository,
                DriverProfileRepository driverProfileRepository
        ) {
            return new CustomerOrderActionService(
                    orderRepository,
                    transitionService,
                    orderRatingRepository,
                    driverProfileRepository);
        }

        @Bean
        AfterCommitEventCollector afterCommitEventCollector() {
            return new AfterCommitEventCollector();
        }
    }

    static class AfterCommitEventCollector {

        private final List<OrderStatusChangedEvent> events = new ArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onOrderStatusChanged(OrderStatusChangedEvent event) {
            events.add(event);
        }

        List<OrderStatusChangedEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }

    static class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<TransactionState> current =
                ThreadLocal.withInitial(TransactionState::new);

        @Override
        protected Object doGetTransaction() {
            return current.get();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionState) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((TransactionState) transaction).active = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No resource commit is needed; Spring still executes transaction synchronizations.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No resource rollback is needed; Spring still skips AFTER_COMMIT listeners.
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((TransactionState) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((TransactionState) transaction).active = false;
            current.remove();
        }

        private static class TransactionState implements SmartTransactionObject {
            private boolean active;
            private boolean rollbackOnly;

            @Override
            public boolean isRollbackOnly() {
                return rollbackOnly;
            }
        }
    }
}
