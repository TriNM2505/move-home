package vn.movehome.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.driver.finance.WithdrawalRequestRepository;
import vn.movehome.backend.dto.admin.list.CustomerListItem;
import vn.movehome.backend.dto.admin.list.DriverListItem;
import vn.movehome.backend.dto.admin.list.OrderListItem;
import vn.movehome.backend.dto.admin.list.WithdrawalListItem;
import vn.movehome.backend.dto.admin.list.WithdrawalListItemRaw;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminListServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    private AdminListService service;

    @BeforeEach
    void setUp() {
        service = new AdminListService(orderRepository, userRepository, withdrawalRequestRepository);
    }

    @Test
    void listOrdersReturnsRepositoryPageWithPaginationTotalsAndSortedContent() {
        OrderListItem item = new OrderListItem(
                UUID.randomUUID(),
                "MH-202606-001",
                "Customer One",
                "Driver One",
                "TRUCK_1000KG",
                "District 1",
                "District 7",
                new BigDecimal("1200000"),
                "COMPLETED",
                OffsetDateTime.parse("2026-06-10T08:00:00Z"),
                OffsetDateTime.parse("2026-06-12T01:00:00Z"));
        when(orderRepository.findAdminOrderList(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(2, 10), 21));

        Page<OrderListItem> result = service.listOrders(
                "COMPLETED", "MH-202606", null, null, 2, 10, "scheduled_at,desc");

        assertThat(result.getContent()).containsExactly(item);
        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getTotalPages()).isEqualTo(3);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAdminOrderList(
                eq("COMPLETED"), eq("MH-202606"), eq(null), eq(null), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().toString()).contains("so.scheduledAt: DESC");
    }

    @Test
    void listOrdersNormalizesFiltersAndConvertsVietnamDateRangeToUtc() {
        when(orderRepository.findAdminOrderList(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listOrders("ALL", "  Nguyen Van A  ", "2026-06-01", "2026-06-02",
                1, 20, "total_quote,asc");

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAdminOrderList(
                eq(null),
                eq("Nguyen Van A"),
                fromCaptor.capture(),
                toCaptor.capture(),
                pageableCaptor.capture());

        assertThat(fromCaptor.getValue()).isEqualTo(OffsetDateTime.of(2026, 5, 31, 17, 0, 0, 0, ZoneOffset.UTC));
        assertThat(toCaptor.getValue()).isEqualTo(OffsetDateTime.of(2026, 6, 2, 17, 0, 0, 0, ZoneOffset.UTC));
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort().toString()).contains("so.totalQuote: ASC");
    }

    @Test
    void listWithdrawalsMasksSensitiveBankFieldsAfterRepositoryProjection() {
        UUID withdrawalId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        WithdrawalListItemRaw raw = new WithdrawalListItemRaw(
                withdrawalId,
                driverId,
                "Tran Driver",
                new BigDecimal("1500000"),
                "VCB",
                "1234567890",
                "PROCESSED",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "Admin One",
                "BANK-TXN-1234");
        when(withdrawalRequestRepository.findAdminWithdrawalList(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(raw)));

        Page<WithdrawalListItem> result = service.listWithdrawals(
                "PROCESSED", " TXN-1234 ", null, null, 0, 10, "requested_at,desc");

        assertThat(result.getContent()).hasSize(1);
        WithdrawalListItem item = result.getContent().get(0);
        assertThat(item.id()).isEqualTo(withdrawalId);
        assertThat(item.driverId()).isEqualTo(driverId);
        assertThat(item.bankAccountMasked()).isEqualTo("******7890");
        assertThat(item.bankTxnRefMasked()).isEqualTo("***1234");
        verify(withdrawalRequestRepository).findAdminWithdrawalList(
                eq("PROCESSED"), eq("TXN-1234"), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void listOrdersRejectsInvalidPaginationStatusSortSearchAndDateRange() {
        assertThatThrownBy(() -> service.listOrders("ALL", null, null, null, -1, 10, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_PAGINATION");
        assertThatThrownBy(() -> service.listOrders("UNKNOWN", null, null, null, 0, 10, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_STATUS_FILTER");
        assertThatThrownBy(() -> service.listOrders("ALL", null, null, null, 0, 10, "created_at,sideways"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_SORT");
        assertThatThrownBy(() -> service.listOrders("ALL", "bad\nterm", null, null, 0, 10, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_SEARCH_TERM");
        assertThatThrownBy(() -> service.listOrders("ALL", null, "2026-06-03", "2026-06-02", 0, 10, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_DATE_RANGE");
    }

    @Test
    void listDriversAndCustomersForwardSupportedFiltersPaginationAndSortToRepositories() {
        DriverListItem driver = new DriverListItem(
                UUID.randomUUID(),
                "Driver One",
                "driver@movehome.vn",
                "0900000001",
                "TRUCK_500KG",
                "51A-12345",
                "ACTIVE",
                new BigDecimal("4.80"),
                12L,
                new BigDecimal("4500000"),
                new BigDecimal("900000"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-15T00:00:00Z"));
        CustomerListItem customer = new CustomerListItem(
                UUID.randomUUID(),
                "Customer One",
                "customer@movehome.vn",
                "0900000002",
                "LOCKED",
                5L,
                new BigDecimal("2500000"),
                new BigDecimal("150000"),
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                true);
        when(userRepository.findAdminDriverList(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(driver), PageRequest.of(0, 20), 1));
        when(userRepository.findAdminCustomerList(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(1, 50), 51));

        Page<DriverListItem> drivers = service.listDrivers(
                "ACTIVE", " Driver One ", 0, 20, "average_rating,asc");
        Page<CustomerListItem> customers = service.listCustomers(
                "LOCKED", " customer@movehome.vn ", 1, 50, "total_spent,desc");

        assertThat(drivers.getContent()).containsExactly(driver);
        assertThat(drivers.getTotalElements()).isEqualTo(1);
        assertThat(customers.getContent()).containsExactly(customer);
        assertThat(customers.getTotalElements()).isEqualTo(51);
        assertThat(customers.getTotalPages()).isEqualTo(2);

        ArgumentCaptor<Pageable> driverPageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> customerPageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAdminDriverList(eq("ACTIVE"), eq("Driver One"), driverPageable.capture());
        verify(userRepository).findAdminCustomerList(eq("LOCKED"), eq("customer@movehome.vn"), customerPageable.capture());
        assertThat(driverPageable.getValue().getSort().toString()).contains("dp.averageRating: ASC");
        assertThat(customerPageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(customerPageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(customerPageable.getValue().getSort().toString()).contains("cw.totalSpent: DESC");
    }
}
