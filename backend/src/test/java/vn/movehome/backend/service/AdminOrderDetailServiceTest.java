package vn.movehome.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.admin.detail.AdminOrderDetailResponse;
import vn.movehome.backend.entity.Transaction;
import vn.movehome.backend.entity.TransactionType;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.TransactionRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOrderDetailServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final AdminOrderDetailService service =
            new AdminOrderDetailService(orderRepository, userRepository, transactionRepository);

    @Test
    void orderDetailThrowsNotFoundWhenOrderMissing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.orderDetail(orderId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).startsWith("ORDER_NOT_FOUND|");
                });
    }

    @Test
    void orderDetailAggregatesOrderPartiesPricingTimelineAndTransactions() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID driverEarningUserId = UUID.randomUUID();
        UUID unknownUserId = UUID.randomUUID();

        User customer = User.builder()
                .id(customerId)
                .fullName("Nguyen Van A")
                .phone("0901234567")
                .build();
        User driver = User.builder()
                .id(driverId)
                .fullName("Tran Van B")
                .phone("0912345678")
                .build();
        User driverEarningUser = User.builder()
                .id(driverEarningUserId)
                .fullName("Tai xe nhan tien")
                .phone(null)
                .build();

        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202607-100")
                .status("COMPLETED")
                .vehicleType("TRUCK_1000KG")
                .porterCount(2)
                .distanceKm(new BigDecimal("12.50"))
                .scheduledAt(OffsetDateTime.parse("2026-07-10T01:00:00Z"))
                .createdAt(OffsetDateTime.parse("2026-07-09T00:00:00Z"))
                .notes("Ghi chu don hang")
                .cancellationReason(null)
                .customerId(customerId)
                .driverId(driverId)
                .pickupAddress("123 Nguyen Trai")
                .pickupDistrict("Thanh Xuan")
                .dropoffAddress("456 Le Loi")
                .dropoffDistrict("Ha Dong")
                .baseFare(new BigDecimal("500000"))
                .peakSurcharge(new BigDecimal("50000"))
                .alleySurcharge(new BigDecimal("20000"))
                .floorSurcharge(new BigDecimal("30000"))
                .porterFee(new BigDecimal("100000"))
                .totalQuote(new BigDecimal("700000"))
                .commissionRateSnapshot(new BigDecimal("0.3000"))
                .arrivedAt(OffsetDateTime.parse("2026-07-10T01:30:00Z"))
                .startedAt(OffsetDateTime.parse("2026-07-10T01:45:00Z"))
                .finalPaidAt(OffsetDateTime.parse("2026-07-10T03:00:00Z"))
                .completedAt(OffsetDateTime.parse("2026-07-10T03:15:00Z"))
                .earningReleasedAt(OffsetDateTime.parse("2026-07-10T05:15:00Z"))
                .cancelledAt(null)
                .build();

        Transaction depositTopUp = tx(TransactionType.DEPOSIT_TOP_UP, driverId, "3000000");
        Transaction depositRefund = tx(TransactionType.DEPOSIT_REFUND, driverId, "-3000000");
        Transaction orderPayment = tx(TransactionType.ORDER_PAYMENT, customerId, "700000");
        Transaction walletTopUp = tx(TransactionType.WALLET_TOP_UP, customerId, "500000");
        Transaction driverEarning = tx(TransactionType.DRIVER_EARNING, driverEarningUserId, "490000");
        Transaction platformFee = tx(TransactionType.PLATFORM_FEE, null, "210000");
        Transaction damageDeduction = tx(TransactionType.DAMAGE_DEDUCTION, driverId, "-50000");
        Transaction withdrawal = tx(TransactionType.WITHDRAWAL, driverId, "-490000");
        Transaction refund = tx(TransactionType.REFUND, customerId, "-700000");
        Transaction unknownTypeTx = txWithNullType(unknownUserId, "1");

        List<Transaction> txs = List.of(
                depositTopUp, depositRefund, orderPayment, walletTopUp, driverEarning,
                platformFee, damageDeduction, withdrawal, refund, unknownTypeTx);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(txs);
        // userRepository.findAllById tra ve customer + driver + driverEarningUser; unknownUserId
        // KHONG nam trong ket qua nay de mo phong truong hop "user bi xoa/khong ton tai".
        when(userRepository.findAllById(anySetContaining(customerId, driverId, driverEarningUserId, unknownUserId)))
                .thenReturn(List.of(customer, driver, driverEarningUser));

        AdminOrderDetailResponse response = service.orderDetail(orderId);

        assertThat(response.order().id()).isEqualTo(orderId);
        assertThat(response.order().orderCode()).isEqualTo("MH-202607-100");
        assertThat(response.order().cancellationReason()).isNull();
        assertThat(response.customer().fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.customer().phoneMasked()).isEqualTo("****567");
        assertThat(response.driver().fullName()).isEqualTo("Tran Van B");
        assertThat(response.driver().phoneMasked()).isEqualTo("****678");
        assertThat(response.pickup().address()).isEqualTo("123 Nguyen Trai");
        assertThat(response.pickup().district()).isEqualTo("Thanh Xuan");
        assertThat(response.dropoff().address()).isEqualTo("456 Le Loi");
        assertThat(response.pricing().totalQuote()).isEqualByComparingTo("700000");
        assertThat(response.pricing().commissionRateSnapshot()).isEqualByComparingTo("0.3000");

        assertThat(response.timeline()).extracting(AdminOrderDetailResponse.TimelineItem::label)
                .containsExactly(
                        "Tạo đơn",
                        "Tài xế đến điểm đón",
                        "Bắt đầu vận chuyển",
                        "Khách thanh toán phần còn lại",
                        "Hoàn thành",
                        "Giải ngân cho tài xế");

        assertThat(response.transactions()).hasSize(10);
        assertThat(response.transactions())
                .extracting(AdminOrderDetailResponse.TransactionItem::typeLabel)
                .containsExactly(
                        "Đặt cọc tài xế",
                        "Hoàn cọc tài xế",
                        "Thanh toán đơn",
                        "Nạp ví khách hàng",
                        "Thu nhập tài xế",
                        "Phí nền tảng",
                        "Khấu trừ khiếu nại",
                        "Rút tiền",
                        "Hoàn tiền",
                        "Giao dịch");
        assertThat(response.transactions())
                .extracting(AdminOrderDetailResponse.TransactionItem::userName)
                .containsExactly(
                        "Tran Van B",
                        "Tran Van B",
                        "Nguyen Van A",
                        "Nguyen Van A",
                        "Tai xe nhan tien",
                        "Hệ thống",
                        "Tran Van B",
                        "Tran Van B",
                        "Nguyen Van A",
                        "Hệ thống");
    }

    @Test
    void orderDetailReturnsNullPartiesWhenOrderHasNoCustomerOrDriverAssigned() {
        UUID orderId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202607-200")
                .status("PENDING_PAYMENT")
                .customerId(null)
                .driverId(null)
                .totalQuote(new BigDecimal("300000"))
                .pickupAddress("A")
                .dropoffAddress("B")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        AdminOrderDetailResponse response = service.orderDetail(orderId);

        assertThat(response.customer()).isNull();
        assertThat(response.driver()).isNull();
        assertThat(response.timeline()).isEmpty();
        assertThat(response.transactions()).isEmpty();
    }

    @Test
    void orderDetailReturnsNullPartyWhenAssignedUserNoLongerExists() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202607-300")
                .status("CANCELLED")
                .customerId(customerId)
                .driverId(null)
                .totalQuote(new BigDecimal("300000"))
                .pickupAddress("A")
                .dropoffAddress("B")
                .cancellationReason("Khach doi y")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());
        when(transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        AdminOrderDetailResponse response = service.orderDetail(orderId);

        assertThat(response.customer()).isNull();
        assertThat(response.order().cancellationReason()).isEqualTo("Khach doi y");
    }

    @Test
    void orderDetailMasksShortAndBlankPhoneNumbers() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User customerShortPhone = User.builder().id(customerId).fullName("Short Phone").phone("999").build();
        User driverBlankPhone = User.builder().id(driverId).fullName("Blank Phone").phone("   ").build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202607-400")
                .status("ACCEPTED")
                .customerId(customerId)
                .driverId(driverId)
                .totalQuote(new BigDecimal("300000"))
                .pickupAddress("A")
                .dropoffAddress("B")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerShortPhone));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driverBlankPhone));
        when(transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        AdminOrderDetailResponse response = service.orderDetail(orderId);

        assertThat(response.customer().phoneMasked()).isEqualTo("***");
        assertThat(response.driver().phoneMasked()).isNull();
    }

    @Test
    void orderDetailResolvesUserNameAsHeThongWhenTransactionUserHasNullFullName() {
        UUID orderId = UUID.randomUUID();
        UUID userIdWithoutName = UUID.randomUUID();
        User userWithoutName = User.builder().id(userIdWithoutName).fullName(null).phone(null).build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202607-500")
                .status("COMPLETED")
                .customerId(null)
                .driverId(null)
                .totalQuote(new BigDecimal("300000"))
                .pickupAddress("A")
                .dropoffAddress("B")
                .build();
        Transaction platformFee = tx(TransactionType.PLATFORM_FEE, userIdWithoutName, "10000");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(platformFee));
        when(userRepository.findAllById(anySetContaining(userIdWithoutName)))
                .thenReturn(List.of(userWithoutName));

        AdminOrderDetailResponse response = service.orderDetail(orderId);

        assertThat(response.transactions()).singleElement()
                .extracting(AdminOrderDetailResponse.TransactionItem::userName)
                .isEqualTo("Hệ thống");
    }

    @Test
    void orderDetailMasksNullCustomerPhoneAsNull() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customerWithoutPhone = User.builder().id(customerId).fullName("No Phone").phone(null).build();
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202607-600")
                .status("PENDING_PAYMENT")
                .customerId(customerId)
                .driverId(null)
                .totalQuote(new BigDecimal("300000"))
                .pickupAddress("A")
                .dropoffAddress("B")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customerWithoutPhone));
        when(transactionRepository.findByRelatedOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        AdminOrderDetailResponse response = service.orderDetail(orderId);

        assertThat(response.customer().phoneMasked()).isNull();
    }

    private Transaction tx(TransactionType type, UUID userId, String amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(type)
                .userId(userId)
                .amount(new BigDecimal(amount))
                .build();
    }

    private Transaction txWithNullType(UUID userId, String amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(null)
                .userId(userId)
                .amount(new BigDecimal(amount))
                .build();
    }

    /**
     * Mockito can chinh xac Set<UUID> voi thu tu khong on dinh (HashSet), nen dung matcher
     * dua tren noi dung (containsAll + kich thuoc) thay vi so sanh tham chieu true.
     */
    private java.util.Set<UUID> anySetContaining(UUID... ids) {
        return org.mockito.ArgumentMatchers.argThat(set -> {
            if (set == null) {
                return false;
            }
            List<UUID> expected = new ArrayList<>(List.of(ids));
            return set.containsAll(expected) && set.size() == expected.size();
        });
    }
}
