package vn.movehome.backend.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.CustomerRefundService;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.service.AuditService;
import vn.movehome.backend.service.NotificationService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra logic tien + guard cua ManagerIncidentService (V44).
 * Trong tam: compensate tinh dung refund = coc 30% + 200k, tru dung 200k tu tai xe (coc truoc),
 * va cac guard (chua qua 15 phut / da co tai xe nhan lai). confirm ban don lai pool dung.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagerIncidentServiceTest {

    @Mock private DriverIncidentReportRepository reportRepository;
    @Mock private DriverIncidentPhotoService photoService;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRefundService customerRefundService;
    @Mock private DriverWalletRepository driverWalletRepository;
    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private ManagerIncidentService service;

    private final UUID incidentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private User actor;

    @BeforeEach
    void setUp() {
        service = new ManagerIncidentService(
                reportRepository, photoService, orderRepository, userRepository, customerRefundService,
                driverWalletRepository, driverProfileRepository, transactionRepository, auditService,
                notificationService);
        ReflectionTestUtils.setField(service, "reassignMinutes", 15L);
        ReflectionTestUtils.setField(service, "compensationAmountVnd", 200000L);

        actor = new User();
        actor.setId(UUID.randomUUID());
        actor.setEmail("manager@movehome.vn");

        // Stub cho detail() goi o cuoi confirm/compensate.
        User driver = new User();
        driver.setId(driverId);
        driver.setFullName("Tai xe A");
        driver.setStatus(UserStatus.ACTIVE);
        User customer = new User();
        customer.setId(customerId);
        customer.setFullName("Khach B");
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(photoService.signedUrls(any())).thenReturn(List.of());
    }

    private ServiceOrder order(String status, UUID driver, BigDecimal total) {
        return ServiceOrder.builder()
                .id(orderId).orderCode("MH-TEST-001").customerId(customerId).driverId(driver)
                .pickupAddress("A").dropoffAddress("B")
                .scheduledAt(OffsetDateTime.now(ZoneOffset.UTC))
                .status(status).totalQuote(total)
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .build();
    }

    private DriverIncidentReport report(String status, OffsetDateTime deadline) {
        return DriverIncidentReport.builder()
                .id(incidentId).orderId(orderId).driverId(driverId)
                .reason("Hong xe").orderStatusSnapshot("IN_PROGRESS")
                .status(status).reassignDeadline(deadline)
                .build();
    }

    @Test
    void compensate_refundsDepositPlus200k_andDeductsFromDriverDeposit() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverProfile profile = DriverProfile.builder().depositAmount(new BigDecimal("3000000")).build();

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        // Coc 30% = 300.000; boi thuong 200.000 -> hoan khach 500.000.
        verify(customerRefundService).refundForCancellation(
                eq(customerId), eq(orderId), eq(new BigDecimal("500000")), any());
        // Tru 200.000 tu COC tai xe (3.000.000 -> 2.800.000); vi khong bi dung.
        assertEquals(0, profile.getDepositAmount().compareTo(new BigDecimal("2800000")));
        verify(driverWalletRepository, org.mockito.Mockito.never()).findByDriverIdForUpdate(any());
        // Trang thai cuoi.
        assertEquals("CANCELLED", order.getStatus());
        assertEquals(DriverIncidentReport.STATUS_COMPENSATED, report.getStatus());
        assertEquals(0, report.getRefundAmount().compareTo(new BigDecimal("500000")));
        assertEquals(0, report.getPenaltyAmount().compareTo(new BigDecimal("200000")));
    }

    @Test
    void compensate_beforeDeadline_throwsConflict() {
        OffsetDateTime future = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_CONFIRMED, future)));

        assertThrows(ResponseStatusException.class, () -> service.compensate(incidentId, actor));
        verify(customerRefundService, org.mockito.Mockito.never())
                .refundForCancellation(any(), any(), any(), any());
    }

    @Test
    void compensate_whenOrderAlreadyReassigned_throwsConflict() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_CONFIRMED, past)));
        // Don da co tai xe khac nhan (khong con o pool CONFIRMED/driver null).
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order("ACCEPTED", UUID.randomUUID(), new BigDecimal("1000000"))));

        assertThrows(ResponseStatusException.class, () -> service.compensate(incidentId, actor));
        verify(customerRefundService, org.mockito.Mockito.never())
                .refundForCancellation(any(), any(), any(), any());
    }

    @Test
    void confirm_returnsOrderToPool_andSetsDeadline() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        ServiceOrder order = order("IN_PROGRESS", driverId, new BigDecimal("1000000"));
        order.setArrivedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.confirm(incidentId, actor);

        assertEquals("CONFIRMED", order.getStatus());
        assertNull(order.getDriverId());
        assertNull(order.getArrivedAt());
        assertEquals(DriverIncidentReport.STATUS_CONFIRMED, report.getStatus());
        // Han 15 phut duoc set.
        org.junit.jupiter.api.Assertions.assertNotNull(report.getReassignDeadline());
    }
}
