package vn.movehome.backend.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dispute.CustomerRefundService;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.Transaction;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // ===================== list() =====================

    @Test
    void list_noStatusFilter_mapsFieldsFromOrderAndDriver() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, now.minusMinutes(1));
        report.setRefundAmount(new BigDecimal("500000"));
        report.setPenaltyAmount(new BigDecimal("200000"));
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));

        when(reportRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Page<IncidentListItem> result = service.list(null, 0, 20);

        assertEquals(1, result.getTotalElements());
        IncidentListItem item = result.getContent().get(0);
        assertEquals(incidentId, item.id());
        assertEquals(orderId, item.orderId());
        assertEquals("MH-TEST-001", item.orderCode());
        assertEquals(driverId, item.driverId());
        assertEquals("Tai xe A", item.driverName());
        assertEquals("Hong xe", item.reason());
        assertEquals(DriverIncidentReport.STATUS_CONFIRMED, item.status());
        assertEquals("IN_PROGRESS", item.orderStatusSnapshot());
        assertTrue(item.overdue());
        assertEquals(0, item.refundAmount().compareTo(new BigDecimal("500000")));
        assertEquals(0, item.penaltyAmount().compareTo(new BigDecimal("200000")));
        org.junit.jupiter.api.Assertions.assertNotNull(item.reassignDeadline());
        // createdAt/confirmedAt khong duoc set trong helper report() (do DB quan ly) — chi goi accessor de phu coverage.
        assertNull(item.createdAt());
        assertNull(item.confirmedAt());
        verify(reportRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
        verify(reportRepository, never()).findByStatusOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void list_whenOrderAndDriverMissing_returnsNullOrderCodeAndDriverNameAndNotOverdue() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        when(reportRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        Page<IncidentListItem> result = service.list(null, 0, 20);

        IncidentListItem item = result.getContent().get(0);
        assertNull(item.orderCode());
        assertNull(item.driverName());
        assertFalse(item.overdue());
    }

    @Test
    void list_withStatusFilter_normalizesAndUsesFindByStatus() {
        when(reportRepository.findByStatusOrderByCreatedAtAsc(eq(DriverIncidentReport.STATUS_PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<IncidentListItem> result = service.list("pending", 0, 20);

        assertEquals(0, result.getTotalElements());
        verify(reportRepository).findByStatusOrderByCreatedAtAsc(eq(DriverIncidentReport.STATUS_PENDING), any(Pageable.class));
    }

    @Test
    void list_statusAll_treatedAsNoFilter() {
        when(reportRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list("all", 0, 20);

        verify(reportRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }

    @Test
    void list_statusBlank_treatedAsNoFilter() {
        when(reportRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list("   ", 0, 20);

        verify(reportRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }

    @Test
    void list_invalidStatus_throwsUnprocessableEntity() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list("FOO", 0, 20));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("VALIDATION_ERROR|Trạng thái sự cố không hợp lệ.", ex.getReason());
    }

    @Test
    void list_negativePage_throwsUnprocessableEntity() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list(null, -1, 20));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("VALIDATION_ERROR|Số trang không hợp lệ.", ex.getReason());
    }

    @Test
    void list_sizeZero_throwsUnprocessableEntity() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list(null, 0, 0));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.", ex.getReason());
    }

    @Test
    void list_sizeTooLarge_throwsUnprocessableEntity() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.list(null, 0, 101));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("VALIDATION_ERROR|Kích thước trang phải từ 1 đến 100.", ex.getReason());
    }

    @Test
    void defaultPageSize_returnsTwenty() {
        assertEquals(20, service.defaultPageSize());
    }

    // ===================== detail() =====================

    @Test
    void detail_reportNotFound_throwsNotFound() {
        when(reportRepository.findById(incidentId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.detail(incidentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("INCIDENT_NOT_FOUND|Không tìm thấy sự cố vận chuyển.", ex.getReason());
    }

    @Test
    void detail_whenOrderMissing_returnsNullOrderFieldsAndNullDeposit() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        IncidentDetailResponse response = service.detail(incidentId);

        assertNull(response.orderCode());
        assertNull(response.orderStatusCurrent());
        assertNull(response.depositEstimate());
        assertNull(response.order());
        assertNull(response.customerId());
        assertNull(response.customerName());
        assertNull(response.customerPhone());
        // Driver van duoc tra ve vi lookup doc lap voi order.
        assertEquals("Tai xe A", response.driverName());
    }

    @Test
    void detail_whenCustomerMissing_returnsNullCustomerFieldsButKeepsOrderCode() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        ServiceOrder order = order("ACCEPTED", driverId, new BigDecimal("1000000"));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        IncidentDetailResponse response = service.detail(incidentId);

        assertNull(response.customerName());
        assertNull(response.customerPhone());
        assertEquals(customerId, response.customerId());
        assertEquals("MH-TEST-001", response.orderCode());
        assertEquals("ACCEPTED", response.orderStatusCurrent());
        assertEquals(0, response.depositEstimate().compareTo(new BigDecimal("300000")));
        assertEquals(0, response.compensationAmount().compareTo(new BigDecimal("200000")));
        org.junit.jupiter.api.Assertions.assertNotNull(response.order());
    }

    // ===================== confirm() guards =====================

    @Test
    void confirm_incidentNotFound_throwsNotFound() {
        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(incidentId, actor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("INCIDENT_NOT_FOUND|Không tìm thấy sự cố vận chuyển.", ex.getReason());
    }

    @Test
    void confirm_whenReportNotPending_throwsConflict() {
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_CONFIRMED, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(incidentId, actor));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("INCIDENT_ALREADY_PROCESSED|Sự cố đã được xử lý.", ex.getReason());
    }

    @Test
    void confirm_whenOrderNotFound_throwsNotFound() {
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_PENDING, null)));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(incidentId, actor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.", ex.getReason());
    }

    @Test
    void confirm_whenOrderDeleted_throwsNotFound() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        ServiceOrder order = order("IN_PROGRESS", driverId, new BigDecimal("1000000"));
        order.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(incidentId, actor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.", ex.getReason());
    }

    @Test
    void confirm_whenDriverMismatch_throwsConflict() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        ServiceOrder order = order("IN_PROGRESS", UUID.randomUUID(), new BigDecimal("1000000"));
        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(incidentId, actor));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("ORDER_STATE_CHANGED|Đơn đã đổi trạng thái, không thể xác nhận sự cố.", ex.getReason());
    }

    @Test
    void confirm_whenOrderStatusNotAcceptedOrInProgress_throwsConflict() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        ServiceOrder order = order("CANCELLED", driverId, new BigDecimal("1000000"));
        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(incidentId, actor));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("ORDER_STATE_CHANGED|Đơn đã đổi trạng thái, không thể xác nhận sự cố.", ex.getReason());
    }

    @Test
    void confirm_whenNotificationFails_stillSucceeds() {
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_PENDING, null);
        ServiceOrder order = order("ACCEPTED", driverId, new BigDecimal("1000000"));
        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("boom")).when(notificationService)
                .create(any(), any(), any(), any());

        IncidentDetailResponse response = service.confirm(incidentId, actor);

        assertEquals(DriverIncidentReport.STATUS_CONFIRMED, response.status());
    }

    // ===================== compensate() guards & deduction paths =====================

    @Test
    void compensate_incidentNotFound_throwsNotFound() {
        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.compensate(incidentId, actor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("INCIDENT_NOT_FOUND|Không tìm thấy sự cố vận chuyển.", ex.getReason());
    }

    @Test
    void compensate_whenReportNotConfirmed_throwsConflict() {
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_PENDING, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.compensate(incidentId, actor));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("INCIDENT_NOT_CONFIRMED|Chỉ hoàn cọc + bồi thường cho sự cố đã xác nhận.", ex.getReason());
    }

    @Test
    void compensate_whenOrderNotFound_throwsNotFound() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_CONFIRMED, past)));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.compensate(incidentId, actor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.", ex.getReason());
    }

    @Test
    void compensate_whenOrderDeleted_throwsNotFound() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        order.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(reportRepository.findByIdForUpdate(incidentId))
                .thenReturn(Optional.of(report(DriverIncidentReport.STATUS_CONFIRMED, past)));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.compensate(incidentId, actor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("ORDER_NOT_FOUND|Không tìm thấy đơn hàng.", ex.getReason());
    }

    @Test
    void compensate_deductsFromWallet_whenDepositInsufficient() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverProfile profile = DriverProfile.builder().depositAmount(new BigDecimal("50000")).build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("500000")).build();

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        assertEquals(0, profile.getDepositAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("350000")));
        verify(driverWalletRepository).insertIfMissing(driverId);
        assertEquals(0, report.getPenaltyAmount().compareTo(new BigDecimal("200000")));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).saveAndFlush(txCaptor.capture());
        Transaction walletTx = txCaptor.getAllValues().get(1);
        assertEquals(0, walletTx.getAmount().compareTo(new BigDecimal("-150000")));
        assertEquals(0, walletTx.getBalanceAfter().compareTo(new BigDecimal("350000")));
        // Khong bi khoa tai khoan vi da thu du tien.
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void compensate_whenProfileMissing_deductsFullyFromWallet() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("1000000")).build();

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("800000")));
        assertEquals(0, report.getPenaltyAmount().compareTo(new BigDecimal("200000")));
        verify(driverProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void compensate_whenProfileDepositAndWalletBalanceNull_treatsAsZero() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverProfile profile = DriverProfile.builder().depositAmount(null).build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(null).build();
        User driver = new User();
        driver.setId(driverId);
        driver.setFullName("Tai xe A");
        driver.setStatus(UserStatus.ACTIVE);

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        // Ca coc va vi deu null -> coi nhu 0, khong thu duoc dong nao, tai xe bi khoa.
        assertEquals(0, report.getPenaltyAmount().compareTo(BigDecimal.ZERO));
        assertEquals(UserStatus.SUSPENDED, driver.getStatus());
    }

    @Test
    void compensate_whenWalletNotFound_throwsConflict() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.compensate(incidentId, actor));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("DRIVER_WALLET_NOT_FOUND|Không tìm thấy ví tài xế.", ex.getReason());
    }

    @Test
    void compensate_whenDepositAndWalletBothInsufficient_suspendsDriverWithUncoveredAmount() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverProfile profile = DriverProfile.builder().depositAmount(new BigDecimal("50000")).build();
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(new BigDecimal("30000")).build();
        User driver = new User();
        driver.setId(driverId);
        driver.setFullName("Tai xe A");
        driver.setStatus(UserStatus.ACTIVE);

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        // Thu duoc 50.000 (coc) + 30.000 (vi) = 80.000; con thieu 120.000.
        assertEquals(0, report.getPenaltyAmount().compareTo(new BigDecimal("80000")));
        assertEquals(UserStatus.SUSPENDED, driver.getStatus());
        assertEquals(UserStatus.ACTIVE, driver.getSuspensionPreviousStatus());
        assertTrue(driver.getSuspensionReason().contains("Cần nạp 3000000 VND"));
        assertTrue(driver.getSuspensionReason().contains("Còn nợ 120000 VND tiền bồi thường chưa thu được."));
        verify(userRepository).saveAndFlush(driver);
    }

    @Test
    void compensate_whenDriverAlreadySuspended_doesNotOverwriteSuspension() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(BigDecimal.ZERO).build();
        User driver = new User();
        driver.setId(driverId);
        driver.setFullName("Tai xe A");
        driver.setStatus(UserStatus.SUSPENDED);

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        assertEquals(0, report.getPenaltyAmount().compareTo(BigDecimal.ZERO));
        // Da SUSPENDED tu truoc -> khong goi lai saveAndFlush / notification khoa tai khoan.
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void compensate_whenDriverUserMissing_suspendIsNoOp() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        DriverIncidentReport report = report(DriverIncidentReport.STATUS_CONFIRMED, past);
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        DriverWallet wallet = DriverWallet.builder().driverId(driverId).balance(BigDecimal.ZERO).build();

        when(reportRepository.findByIdForUpdate(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());
        when(driverWalletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));
        when(userRepository.findById(driverId)).thenReturn(Optional.empty());
        when(reportRepository.findById(incidentId)).thenReturn(Optional.of(report));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.compensate(incidentId, actor);

        assertEquals(0, report.getPenaltyAmount().compareTo(BigDecimal.ZERO));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    // ===================== helper private — cac nhanh chua duoc bao phu qua luong cong khai =====================

    @Test
    void depositOf_whenCommissionRateSnapshotNull_usesDefaultRate() {
        // order() helper luon set commissionRateSnapshot=0.3000; dung reflection de kiem tra
        // nhanh con lai cua toan tu ba ngoi khi commissionRateSnapshot == null (dung DEFAULT_COMMISSION_RATE).
        ServiceOrder order = order("CONFIRMED", null, new BigDecimal("1000000"));
        order.setCommissionRateSnapshot(null);

        BigDecimal deposit = ReflectionTestUtils.invokeMethod(service, "depositOf", order);

        assertEquals(0, deposit.compareTo(new BigDecimal("300000")));
    }

    @Test
    void suspendDriverForPenalty_whenUncoveredIsZero_omitsDebtSentenceFromReason() {
        // Nhanh con lai cua ternary o dong "Con no ... VND": uncovered.signum() <= 0 -> chuoi rong.
        // Trong luong cong khai suspendDriverForPenalty() chi duoc goi khi remaining > 0 (luon > 0),
        // nen goi truc tiep qua reflection de kiem tra nhanh else nay cua helper private.
        User driver = new User();
        driver.setId(driverId);
        driver.setFullName("Tai xe A");
        driver.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "suspendDriverForPenalty",
                driverId, "MH-TEST-001", BigDecimal.ZERO);

        assertEquals(UserStatus.SUSPENDED, driver.getStatus());
        assertTrue(driver.getSuspensionReason().contains("Cần nạp 3000000 VND"));
        assertFalse(driver.getSuspensionReason().contains("Còn nợ"));
    }

    @Test
    void safeNotify_whenUserIdIsNull_doesNothing() throws Exception {
        // Nhanh rieng cua safeNotify(): userId null -> return som, khong goi notificationService.
        java.lang.reflect.Method safeNotify = ManagerIncidentService.class.getDeclaredMethod(
                "safeNotify", UUID.class, String.class, String.class, String.class);
        safeNotify.setAccessible(true);
        safeNotify.invoke(service, null, "PENALTY_ACCOUNT_LOCKED", "Tieu de", "Noi dung");

        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void money_whenValueIsNull_returnsZero() throws Exception {
        // Nhanh rieng cua money(): value null -> BigDecimal.ZERO. Dung java.lang.reflect truc tiep
        // vi tham so null khien ReflectionTestUtils.invokeMethod khong xac dinh duoc kieu tham so.
        java.lang.reflect.Method money = ManagerIncidentService.class.getDeclaredMethod(
                "money", BigDecimal.class);
        money.setAccessible(true);
        BigDecimal result = (BigDecimal) money.invoke(service, (Object) null);

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }
}
