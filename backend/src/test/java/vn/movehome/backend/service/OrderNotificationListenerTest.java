package vn.movehome.backend.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.movehome.backend.email.notification.EmailService;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.event.OrderStatusChangedEvent;
import vn.movehome.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

        @Mock
        private NotificationService notificationService;

        @Mock
        private EmailService emailService;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private OrderNotificationListener listener;

        @Test
        void ignoresEventWithNullStatus() {
                OrderStatusChangedEvent event = buildEvent(null, UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID(), "ORD-001");

                listener.onOrderStatusChanged(event);

                verify(notificationService, never()).create(any(), any(), any(), any());
                verify(emailService, never()).send(any(), any(), any());
                verify(userRepository, never()).findById(any());
        }

        @Test
        void notifiesCustomerWithEmailWhenStatusAccepted() {
                UUID customerId = UUID.randomUUID();
                String email = "customer@example.com";
                String orderCode = "ORD-002";
                OrderStatusChangedEvent event = buildEvent("ACCEPTED", customerId, null, UUID.randomUUID(), orderCode);
                ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

                Optional<User> userOpt = userWithEmail(email);
                when(userRepository.findById(customerId)).thenReturn(userOpt);

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_ACCEPTED),
                                any(),
                                messageCaptor.capture());
                verify(userRepository, times(1)).findById(customerId);
                verify(emailService, times(1)).send(eq(email), any(), any());
                assertThat(messageCaptor.getValue()).contains(orderCode);
        }

        @Test
        void notifiesCustomerWithoutEmailWhenStatusInProgress() {
                UUID customerId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("IN_PROGRESS", customerId, null, UUID.randomUUID(),
                                "ORD-003");

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_IN_PROGRESS),
                                any(),
                                any());
                verify(emailService, never()).send(any(), any(), any());
                verify(userRepository, never()).findById(any());
        }

        @Test
        void notifiesCustomerWithEmailWhenStatusCompleted() {
                UUID customerId = UUID.randomUUID();
                String email = "customer@example.com";
                OrderStatusChangedEvent event = buildEvent("COMPLETED", customerId, null, UUID.randomUUID(), "ORD-004");

                Optional<User> userOpt = userWithEmail(email);
                when(userRepository.findById(customerId)).thenReturn(userOpt);

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_COMPLETED),
                                any(),
                                any());
                verify(userRepository, times(1)).findById(customerId);
                verify(emailService, times(1)).send(eq(email), any(), any());
        }

        @Test
        void ignoresUnknownStatus() {
                OrderStatusChangedEvent event = buildEvent(
                                "CONFIRMED",
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "ORD-005");

                listener.onOrderStatusChanged(event);

                verify(notificationService, never()).create(any(), any(), any(), any());
                verify(emailService, never()).send(any(), any(), any());
                verify(userRepository, never()).findById(any());
        }

        @Test
        void cancelledByCustomerNotifiesOnlyDriver() {
                UUID customerId = UUID.randomUUID();
                UUID driverId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("CANCELLED", customerId, driverId, customerId, "ORD-006");

                when(userRepository.findById(driverId)).thenReturn(Optional.empty());

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(driverId),
                                eq(NotificationType.ORDER_CANCELLED),
                                any(),
                                any());
                verify(notificationService, never()).create(eq(customerId), any(), any(), any());
                verify(userRepository, times(1)).findById(driverId);
        }

        @Test
        void cancelledByDriverNotifiesOnlyCustomer() {
                UUID customerId = UUID.randomUUID();
                UUID driverId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("CANCELLED", customerId, driverId, driverId, "ORD-007");

                when(userRepository.findById(customerId)).thenReturn(Optional.empty());

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_CANCELLED),
                                any(),
                                any());
                verify(notificationService, never()).create(eq(driverId), any(), any(), any());
                verify(userRepository, times(1)).findById(customerId);
        }

        @Test
        void cancelledByOtherNotifiesBoth() {
                UUID customerId = UUID.randomUUID();
                UUID driverId = UUID.randomUUID();
                UUID changedByUserId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("CANCELLED", customerId, driverId, changedByUserId,
                                "ORD-008");

                Optional<User> customerOpt = userWithEmail("customer@example.com");
                Optional<User> driverOpt = userWithEmail("driver@example.com");
                when(userRepository.findById(customerId)).thenReturn(customerOpt);
                when(userRepository.findById(driverId)).thenReturn(driverOpt);

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_CANCELLED),
                                any(),
                                any());
                verify(notificationService, times(1)).create(
                                eq(driverId),
                                eq(NotificationType.ORDER_CANCELLED),
                                any(),
                                any());
                verify(notificationService, times(2)).create(any(), eq(NotificationType.ORDER_CANCELLED), any(), any());
                verify(emailService, times(2)).send(any(), any(), any());
        }

        @Test
        void cancelledWithMissingDriverNotifiesOnlyCustomer() {
                UUID customerId = UUID.randomUUID();
                UUID changedByUserId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("CANCELLED", customerId, null, changedByUserId, "ORD-009");

                when(userRepository.findById(customerId)).thenReturn(Optional.empty());

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_CANCELLED),
                                any(),
                                any());
                verify(notificationService, times(1)).create(any(), any(), any(), any());
                verify(emailService, never()).send(any(), any(), any());
        }

        @Test
        void continuesWhenNotificationCreateFails() {
                UUID customerId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("ACCEPTED", customerId, null, UUID.randomUUID(), "ORD-010");

                when(notificationService.create(eq(customerId), eq(NotificationType.ORDER_ACCEPTED), any(), any()))
                                .thenThrow(new RuntimeException("create failed"));

                assertThatCode(() -> listener.onOrderStatusChanged(event)).doesNotThrowAnyException();

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_ACCEPTED),
                                any(),
                                any());
                verify(userRepository, never()).findById(any());
                verify(emailService, never()).send(any(), any(), any());
        }

        @Test
        void skipsEmailWhenUserHasBlankEmail() {
                UUID customerId = UUID.randomUUID();
                OrderStatusChangedEvent event = buildEvent("ACCEPTED", customerId, null, UUID.randomUUID(), "ORD-011");

                Optional<User> userOpt = userWithEmail("");
                when(userRepository.findById(customerId)).thenReturn(userOpt);

                listener.onOrderStatusChanged(event);

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_ACCEPTED),
                                any(),
                                any());
                verify(userRepository, times(1)).findById(customerId);
                verify(emailService, never()).send(any(), any(), any());
        }

        @Test
        void swallowsEmailServiceExceptions() {
                UUID customerId = UUID.randomUUID();
                String email = "customer@example.com";
                OrderStatusChangedEvent event = buildEvent("ACCEPTED", customerId, null, UUID.randomUUID(), "ORD-012");

                Optional<User> userOpt = userWithEmail(email);
                when(userRepository.findById(customerId)).thenReturn(userOpt);
                doThrow(new RuntimeException("email failed"))
                                .when(emailService)
                                .send(eq(email), any(), any());

                assertThatCode(() -> listener.onOrderStatusChanged(event)).doesNotThrowAnyException();

                verify(notificationService, times(1)).create(
                                eq(customerId),
                                eq(NotificationType.ORDER_ACCEPTED),
                                any(),
                                any());
                verify(emailService, times(1)).send(eq(email), any(), any());
        }

        private OrderStatusChangedEvent buildEvent(
                        String status,
                        UUID customerId,
                        UUID driverId,
                        UUID changedByUserId,
                        String orderCode) {
                return new OrderStatusChangedEvent(
                                UUID.randomUUID(),
                                orderCode,
                                "PENDING",
                                status,
                                customerId,
                                driverId,
                                changedByUserId,
                                "CUSTOMER",
                                OffsetDateTime.now(ZoneOffset.UTC));
        }

        private Optional<User> userWithEmail(String email) {
                User user = new User();
                user.setEmail(email);
                return Optional.of(user);
        }
}