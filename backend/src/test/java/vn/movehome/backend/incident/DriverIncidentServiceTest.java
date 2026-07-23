package vn.movehome.backend.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.NotificationService;
import vn.movehome.backend.service.NotificationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra DriverIncidentService (V44): tai xe bao su co giua chuyen + resolve khi tai xe khac nhan lai.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverIncidentServiceTest {

    @Mock private DriverIncidentReportRepository reportRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private DriverIncidentService service;

    private final UUID orderId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID incidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DriverIncidentService(reportRepository, orderRepository, userRepository, notificationService);
    }

    private ServiceOrder order(String status, UUID driver) {
        return ServiceOrder.builder()
                .id(orderId).orderCode("MH-TEST-002").customerId(UUID.randomUUID()).driverId(driver)
                .pickupAddress("A").dropoffAddress("B")
                .scheduledAt(OffsetDateTime.now(ZoneOffset.UTC))
                .status(status).totalQuote(new BigDecimal("1000000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
    }

    // ===================== reportIncident =====================

    @Test
    void reportIncident_success_savesPendingReportAndNotifiesActiveManagers() {
        ServiceOrder order = order("ACCEPTED", driverId);
        User manager1 = User.builder().id(UUID.randomUUID()).build();
        User manager2 = User.builder().id(UUID.randomUUID()).build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reportRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).thenReturn(false);
        when(reportRepository.saveAndFlush(any(DriverIncidentReport.class))).thenAnswer(invocation -> {
            DriverIncidentReport arg = invocation.getArgument(0);
            arg.setId(incidentId);
            return arg;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(manager1, manager2));

        UUID result = service.reportIncident(driverId, orderId, "  Xe hỏng giữa đường  ");

        assertThat(result).isEqualTo(incidentId);

        ArgumentCaptor<DriverIncidentReport> captor = ArgumentCaptor.forClass(DriverIncidentReport.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        DriverIncidentReport saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getDriverId()).isEqualTo(driverId);
        assertThat(saved.getReason()).isEqualTo("Xe hỏng giữa đường");
        assertThat(saved.getOrderStatusSnapshot()).isEqualTo("ACCEPTED");
        assertThat(saved.getStatus()).isEqualTo(DriverIncidentReport.STATUS_PENDING);

        verify(notificationService).create(eq(manager1.getId()), eq(NotificationType.DRIVER_INCIDENT_REPORTED),
                anyString(), anyString());
        verify(notificationService).create(eq(manager2.getId()), eq(NotificationType.DRIVER_INCIDENT_REPORTED),
                anyString(), anyString());
    }

    @Test
    void reportIncident_whenOrderNotFound_throwsNotFound() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, "Ly do"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.");
                });
    }

    @Test
    void reportIncident_whenOrderDeleted_throwsNotFound() {
        ServiceOrder order = order("ACCEPTED", driverId);
        order.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, "Ly do"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void reportIncident_whenDriverMismatch_throwsAccessDenied() {
        ServiceOrder order = order("ACCEPTED", UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, "Ly do"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Bạn chỉ có thể báo sự cố cho đơn của chính mình.");
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportIncident_whenOrderStatusNotReportable_throwsConflict() {
        ServiceOrder order = order("AWAITING_FINAL_PAYMENT", driverId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, "Ly do"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "INVALID_STATE|Chỉ báo sự cố khi đang trên đường đến điểm đón hoặc đang giao hàng.");
                });
    }

    @Test
    void reportIncident_whenReasonNull_throwsValidationError() {
        ServiceOrder order = order("ACCEPTED", driverId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng mô tả sự cố.");
                });
    }

    @Test
    void reportIncident_whenReasonBlank_throwsValidationError() {
        ServiceOrder order = order("ACCEPTED", driverId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, "   "))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).isEqualTo("VALIDATION_ERROR|Vui lòng mô tả sự cố."));
    }

    @Test
    void reportIncident_whenReasonTooLong_throwsValidationError() {
        ServiceOrder order = order("ACCEPTED", driverId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        String tooLong = "a".repeat(501);

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, tooLong))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(
                            "VALIDATION_ERROR|Mô tả sự cố không được vượt quá 500 ký tự.");
                });
    }

    @Test
    void reportIncident_whenIncidentAlreadyOpen_throwsConflict() {
        ServiceOrder order = order("IN_PROGRESS", driverId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reportRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).thenReturn(true);

        assertThatThrownBy(() -> service.reportIncident(driverId, orderId, "Ly do"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).isEqualTo(
                            "INCIDENT_ALREADY_OPEN|Đơn này đã có báo cáo sự cố đang được xử lý.");
                });
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportIncident_whenNoActiveManagers_doesNotNotifyAnyone() {
        ServiceOrder order = order("ACCEPTED", driverId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reportRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).thenReturn(false);
        when(reportRepository.saveAndFlush(any(DriverIncidentReport.class))).thenAnswer(invocation -> {
            DriverIncidentReport arg = invocation.getArgument(0);
            arg.setId(incidentId);
            return arg;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of());

        UUID result = service.reportIncident(driverId, orderId, "Ly do");

        assertThat(result).isEqualTo(incidentId);
        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void reportIncident_whenNotifyManagerFails_stillReturnsIncidentId() {
        ServiceOrder order = order("ACCEPTED", driverId);
        User manager = User.builder().id(UUID.randomUUID()).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reportRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).thenReturn(false);
        when(reportRepository.saveAndFlush(any(DriverIncidentReport.class))).thenAnswer(invocation -> {
            DriverIncidentReport arg = invocation.getArgument(0);
            arg.setId(incidentId);
            return arg;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(manager));
        doThrow(new RuntimeException("boom")).when(notificationService)
                .create(any(), any(), any(), any());

        UUID result = service.reportIncident(driverId, orderId, "Ly do");

        assertThat(result).isEqualTo(incidentId);
    }

    // ===================== resolveReassigned =====================

    @Test
    void resolveReassigned_whenOpenIncidentExists_marksResolvedAndNotifiesDriver() {
        DriverIncidentReport report = DriverIncidentReport.builder()
                .id(incidentId).orderId(orderId).driverId(driverId)
                .reason("Hong xe").orderStatusSnapshot("IN_PROGRESS")
                .status(DriverIncidentReport.STATUS_CONFIRMED)
                .build();
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_CONFIRMED))
                .thenReturn(Optional.of(report));

        service.resolveReassigned(orderId);

        assertThat(report.getStatus()).isEqualTo(DriverIncidentReport.STATUS_RESOLVED_REASSIGNED);
        verify(reportRepository).saveAndFlush(report);
        verify(notificationService).create(eq(driverId), eq(NotificationType.DRIVER_INCIDENT_REPORTED),
                anyString(), anyString());
    }

    @Test
    void resolveReassigned_whenNoOpenIncident_doesNothing() {
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_CONFIRMED))
                .thenReturn(Optional.empty());

        service.resolveReassigned(orderId);

        verify(reportRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void resolveReassigned_whenNotifyFails_stillMarksResolved() {
        DriverIncidentReport report = DriverIncidentReport.builder()
                .id(incidentId).orderId(orderId).driverId(driverId)
                .reason("Hong xe").orderStatusSnapshot("IN_PROGRESS")
                .status(DriverIncidentReport.STATUS_CONFIRMED)
                .build();
        when(reportRepository.findFirstByOrderIdAndStatus(orderId, DriverIncidentReport.STATUS_CONFIRMED))
                .thenReturn(Optional.of(report));
        doThrow(new RuntimeException("boom")).when(notificationService)
                .create(any(), any(), any(), any());

        service.resolveReassigned(orderId);

        assertThat(report.getStatus()).isEqualTo(DriverIncidentReport.STATUS_RESOLVED_REASSIGNED);
        verify(reportRepository).saveAndFlush(report);
    }

    @Test
    void safeNotify_whenUserIdIsNull_doesNothing() throws Exception {
        // Nhanh rieng cua safeNotify(): userId null -> return som, khong goi notificationService.
        // Trong luong cong khai report.getDriverId()/manager.getId() luon != null nen dung reflection
        // truc tiep de kiem tra nhanh nay cua helper private.
        java.lang.reflect.Method safeNotify = DriverIncidentService.class.getDeclaredMethod(
                "safeNotify", UUID.class, String.class, String.class);
        safeNotify.setAccessible(true);
        safeNotify.invoke(service, null, "Tieu de", "Noi dung");

        verify(notificationService, never()).create(any(), any(), any(), any());
    }
}
