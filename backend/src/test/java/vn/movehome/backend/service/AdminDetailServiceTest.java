package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.DriverWallet;
import vn.movehome.backend.driver.finance.DriverWalletRepository;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.driver.location.DriverLocation;
import vn.movehome.backend.driver.location.DriverLocationRepository;
import vn.movehome.backend.dto.admin.detail.AuditLogItem;
import vn.movehome.backend.dto.admin.detail.CustomerDetailResponse;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.entity.CustomerWallet;
import vn.movehome.backend.entity.DriverProfile;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRatingRepository;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.AuditLogRepository;
import vn.movehome.backend.repository.DriverDocumentRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.WalletRepository;
import vn.movehome.backend.repository.WalletTransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDetailServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverDocumentRepository driverDocumentRepository;

    @Mock
    private DriverWalletRepository driverWalletRepository;

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderRatingRepository orderRatingRepository;

    @Mock
    private DriverLocationRepository driverLocationRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private AdminDetailService service;

    @BeforeEach
    void setUp() {
        service = new AdminDetailService(
                userRepository,
                driverProfileRepository,
                driverDocumentRepository,
                driverWalletRepository,
                withdrawalRequestRepository,
                orderRepository,
                orderRatingRepository,
                driverLocationRepository,
                walletRepository,
                walletTransactionRepository,
                auditLogRepository);
    }

    @Test
    void driverDetailAggregatesProfileWalletStatsRatingsAndBusyLocation() {
        UUID driverId = UUID.randomUUID();
        UUID activeOrderId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T02:00:00Z");
        User driver = User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .email("driver@movehome.vn")
                .fullName("Driver One")
                .phone("0987654321")
                .emailVerified(true)
                .createdAt(createdAt)
                .build();
        DriverProfile profile = DriverProfile.builder()
                .userId(driverId)
                .licenseNumber("B2-001")
                .licenseClass("B2")
                .licenseExpiryDate(LocalDate.of(2028, 1, 1))
                .vehiclePlate("51A-12345")
                .vehicleType("TRUCK_1000KG")
                .vehicleCapacityKg(1000)
                .depositAmount(new BigDecimal("2000000"))
                .depositPaidAt(OffsetDateTime.parse("2026-06-02T03:00:00Z"))
                .approvedAt(OffsetDateTime.parse("2026-06-03T03:00:00Z"))
                .build();
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .balance(new BigDecimal("700000"))
                .totalEarned(new BigDecimal("1700000"))
                .build();
        DriverLocation location = DriverLocation.builder()
                .driverId(driverId)
                .currentOrderId(activeOrderId)
                .lat(new BigDecimal("10.1234567"))
                .lng(new BigDecimal("106.1234567"))
                .updatedAt(Instant.now())
                .recordedAt(Instant.now())
                .build();

        when(userRepository.findAdminDriverDetailUser(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.findByUserId(driverId)).thenReturn(Optional.of(profile));
        when(driverDocumentRepository.countDocumentsByType(driverId)).thenReturn(List.of(
                docCount("DRIVING_LICENSE", 1L),
                docCount("VEHICLE_REGISTRATION", 2L),
                docCount("VEHICLE_PHOTO", 3L),
                docCount("UNUSED", 99L)));
        when(driverWalletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.sumProcessedAmountByDriver(driverId)).thenReturn(new BigDecimal("1000000"));
        when(orderRepository.countByDriverIdAndStatusAndDeletedAtIsNull(driverId, "COMPLETED")).thenReturn(6L);
        when(orderRepository.countByDriverIdAndStatusAndDeletedAtIsNull(driverId, "CANCELLED")).thenReturn(1L);
        when(orderRepository.countByDriverIdAndStatusAndDeletedAtIsNull(driverId, "DISPUTED")).thenReturn(2L);
        when(orderRatingRepository.averageRatingByDriver(driverId)).thenReturn(Optional.of(new BigDecimal("4.50")));
        when(orderRatingRepository.countRatingsByDriverGroupByStar(driverId)).thenReturn(List.of(
                ratingCount(2, 1L),
                ratingCount(5, 3L),
                ratingCount(9, 7L)));
        when(orderRepository.findRecentOrdersByDriver(eq(driverId), any(Pageable.class))).thenReturn(List.of());
        when(withdrawalRequestRepository.findRecentWithdrawalsByDriver(eq(driverId), any(Pageable.class)))
                .thenReturn(List.of());
        when(driverLocationRepository.findByDriverId(driverId)).thenReturn(Optional.of(location));
        when(orderRepository.findById(activeOrderId)).thenReturn(Optional.of(ServiceOrder.builder()
                .id(activeOrderId)
                .status("ACCEPTED")
                .build()));

        DriverDetailResponse response = service.driverDetail(driverId);

        assertThat(response.user().phoneMasked()).isEqualTo("098****321");
        assertThat(response.documentsSummary().totalCount()).isEqualTo(6);
        assertThat(response.vehicles()).singleElement().satisfies(vehicle -> {
            assertThat(vehicle.plate()).isEqualTo("51A-12345");
            assertThat(vehicle.capacityKg()).isEqualTo(1000);
        });
        assertThat(response.deposit().status()).isEqualTo("PAID");
        assertThat(response.wallet().balance()).isEqualByComparingTo("700000");
        assertThat(response.wallet().totalWithdrawn()).isEqualByComparingTo("1000000");
        assertThat(response.stats().totalRatingsCount()).isEqualTo(11);
        assertThat(response.ratingDistribution().total()).isEqualTo(4);
        assertThat(response.ratingDistribution().average()).isEqualByComparingTo("4.25");
        assertThat(response.onlineStatus()).isEqualTo("BUSY");
        assertThat(response.lastKnownLocation()).isNotNull();
        assertThat(response.allowedActions()).containsExactly("VIEW_AUDIT", "VIEW_ORDER_HISTORY");
    }

    @Test
    void customerDetailAggregatesStatsWalletAndDistrictActivity() {
        UUID customerId = UUID.randomUUID();
        User customer = User.builder()
                .id(customerId)
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .email("customer@movehome.vn")
                .fullName("Customer One")
                .phone("0901234567")
                .emailVerified(true)
                .createdAt(Instant.parse("2026-05-01T00:00:00Z"))
                .build();
        CustomerWallet wallet = CustomerWallet.builder()
                .customerId(customerId)
                .balance(new BigDecimal("300000"))
                .totalSpent(new BigDecimal("2000000"))
                .build();
        OffsetDateTime firstOrder = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        OffsetDateTime lastOrder = OffsetDateTime.parse("2026-06-20T00:00:00Z");

        when(userRepository.findAdminCustomerDetailUser(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.countByCustomerIdAndDeletedAtIsNull(customerId)).thenReturn(8L);
        when(orderRepository.countByCustomerIdAndStatusAndDeletedAtIsNull(customerId, "COMPLETED")).thenReturn(5L);
        when(orderRepository.countByCustomerIdAndStatusAndDeletedAtIsNull(customerId, "CANCELLED")).thenReturn(2L);
        when(orderRepository.countByCustomerIdAndStatusAndDeletedAtIsNull(customerId, "DISPUTED")).thenReturn(1L);
        when(orderRepository.sumCompletedTotalQuoteByCustomer(customerId)).thenReturn(new BigDecimal("2000000"));
        when(orderRepository.findFirstOrderAtByCustomer(customerId)).thenReturn(Optional.of(firstOrder));
        when(orderRepository.findLastOrderAtByCustomer(customerId)).thenReturn(Optional.of(lastOrder));
        when(orderRepository.findRecentOrdersByCustomer(eq(customerId), any(Pageable.class))).thenReturn(List.of());
        when(walletRepository.findByCustomerId(customerId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.sumTopUpByUserId(customerId)).thenReturn(new BigDecimal("2300000"));
        when(walletTransactionRepository.findRecentByUserId(eq(customerId), any(Pageable.class))).thenReturn(List.of(
                new CustomerDetailResponse.RecentWalletTransactionItem(
                        UUID.randomUUID(),
                        "WALLET_TOP_UP",
                        new BigDecimal("500000"),
                        null,
                        OffsetDateTime.now(),
                        "PAYMENT-987654")));
        when(orderRepository.countPickupDistrictsByCustomer(customerId)).thenReturn(List.of(
                districtCount("District 1", 3L),
                districtCount("District 7", 2L)));
        when(orderRepository.countDropoffDistrictsByCustomer(customerId)).thenReturn(List.of(
                districtCount("District 1", 1L),
                districtCount("Thu Duc", 4L)));

        CustomerDetailResponse response = service.customerDetail(customerId);

        assertThat(response.user().phoneMasked()).isEqualTo("090****567");
        assertThat(response.stats().totalOrders()).isEqualTo(8);
        assertThat(response.stats().firstOrderAt()).isEqualTo(firstOrder);
        assertThat(response.walletSummary().balance()).isEqualByComparingTo("300000");
        assertThat(response.walletSummary().totalToppedUp()).isEqualByComparingTo("2300000");
        assertThat(response.recentWalletTransactions()).singleElement()
                .extracting(CustomerDetailResponse.RecentWalletTransactionItem::referenceMasked)
                .isEqualTo("***7654");
        assertThat(response.districtActivity())
                .extracting(CustomerDetailResponse.DistrictActivityItem::district)
                .containsExactly("District 1", "District 7", "Thu Duc");
        assertThat(response.districtActivity().get(0).pickupCount()).isEqualTo(3);
        assertThat(response.districtActivity().get(0).dropoffCount()).isEqualTo(1);
    }

    @Test
    void entityAuditLogValidatesEntityAndConvertsFiltersToRepositoryQuery() {
        UUID driverId = UUID.randomUUID();
        AuditLogItem row = new AuditLogItem(
                UUID.randomUUID(),
                "DRIVER_STATUS_CHANGED",
                UUID.randomUUID(),
                "admin@movehome.vn",
                "USER",
                driverId.toString(),
                "ACTIVE -> SUSPENDED",
                OffsetDateTime.parse("2026-06-10T00:00:00Z"));
        when(userRepository.findAdminDriverDetailUser(driverId)).thenReturn(Optional.of(User.builder()
                .id(driverId)
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .email("driver@movehome.vn")
                .fullName("Driver One")
                .build()));
        when(auditLogRepository.findAdminEntityAuditLog(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<AuditLogItem> result = service.entityAuditLog(
                "drivers",
                driverId,
                " DRIVER_STATUS_CHANGED ",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 10),
                0,
                10);

        assertThat(result.getContent()).containsExactly(row);
        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository).findAdminEntityAuditLog(
                eq("USER"),
                eq(driverId.toString()),
                eq("DRIVER_STATUS_CHANGED"),
                fromCaptor.capture(),
                toCaptor.capture(),
                any(Pageable.class));
        assertThat(fromCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00+07:00"));
        assertThat(toCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-06-11T00:00:00+07:00"));
    }

    @Test
    void entityAuditLogForOrderFoundQueriesOrderAuditRowsWithPagination() {
        UUID orderId = UUID.randomUUID();
        AuditLogItem row = new AuditLogItem(
                UUID.randomUUID(),
                "ORDER_STATUS_CHANGED",
                UUID.randomUUID(),
                "admin@movehome.vn",
                "SERVICE_ORDER",
                orderId.toString(),
                "PENDING -> COMPLETED",
                OffsetDateTime.parse("2026-06-10T00:00:00Z"));
        ServiceOrder order = ServiceOrder.builder()
                .id(orderId)
                .orderCode("MH-202606-001")
                .customerId(UUID.randomUUID())
                .pickupAddress("A")
                .dropoffAddress("B")
                .scheduledAt(OffsetDateTime.parse("2026-06-12T00:00:00Z"))
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .build();
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(auditLogRepository.findAdminEntityAuditLog(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), org.springframework.data.domain.PageRequest.of(1, 10), 11));

        Page<AuditLogItem> result = service.entityAuditLog(
                "orders",
                orderId,
                "ORDER_STATUS_CHANGED",
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10),
                1,
                10);

        assertThat(result.getContent()).containsExactly(row);
        assertThat(result.getTotalElements()).isEqualTo(11);
        assertThat(result.getTotalPages()).isEqualTo(2);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAdminEntityAuditLog(
                eq("SERVICE_ORDER"),
                eq(orderId.toString()),
                eq("ORDER_STATUS_CHANGED"),
                eq(OffsetDateTime.parse("2026-06-10T00:00:00+07:00")),
                eq(OffsetDateTime.parse("2026-06-11T00:00:00+07:00")),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void entityAuditLogForMissingOrderThrowsOrderNotFoundBeforeAuditQuery() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entityAuditLog(
                "orders", orderId, null, null, null, 0, 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");

        verify(auditLogRepository, never()).findAdminEntityAuditLog(any(), any(), any(), any(), any(), any());
    }

    @Test
    void entityAuditLogRejectsBadFiltersBeforeQueryingAuditRows() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.entityAuditLog("admins", id, null, null, null, 0, 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_AUDIT_FILTER");
        assertThatThrownBy(() -> service.entityAuditLog("drivers", id, null, null, null, 0, 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_PAGINATION");
    }

    private DriverDocumentRepository.DocTypeCount docCount(String docType, Long count) {
        return new DriverDocumentRepository.DocTypeCount() {
            @Override
            public String getDocType() {
                return docType;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private OrderRatingRepository.RatingStarCount ratingCount(Integer star, Long count) {
        return new OrderRatingRepository.RatingStarCount() {
            @Override
            public Integer getStar() {
                return star;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private OrderRepository.DistrictCount districtCount(String district, Long count) {
        return new OrderRepository.DistrictCount() {
            @Override
            public String getDistrict() {
                return district;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
