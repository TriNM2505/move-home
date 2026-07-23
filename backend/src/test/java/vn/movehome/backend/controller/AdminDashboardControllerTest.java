package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.movehome.backend.dto.admin.CustomerListItem;
import vn.movehome.backend.dto.admin.DashboardOverviewResponse;
import vn.movehome.backend.dto.admin.DriverListItem;
import vn.movehome.backend.dto.admin.DriverPerformanceResponse;
import vn.movehome.backend.dto.admin.KpiResponse;
import vn.movehome.backend.dto.admin.OrderListItem;
import vn.movehome.backend.dto.admin.OrderStatusDistribution;
import vn.movehome.backend.dto.admin.RecentOrderResponse;
import vn.movehome.backend.dto.admin.RevenueByDayResponse;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.service.AdminDashboardService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDashboardControllerTest {

    private final AdminDashboardService service = mock(AdminDashboardService.class);
    private final AdminDashboardController controller = new AdminDashboardController(service);

    @Test
    void getKpiDelegatesToService() {
        KpiResponse expected = new KpiResponse(1L, 2L, 3L, 4L, 5L,
                BigDecimal.TEN, BigDecimal.ONE, 6L, 7L, 8L);
        when(service.getKpi()).thenReturn(expected);

        assertThat(controller.getKpi()).isEqualTo(expected);
    }

    @Test
    void getRevenueChartDelegatesToService() {
        RevenueByDayResponse expected = new RevenueByDayResponse(List.of(
                new RevenueByDayResponse.RevenuePoint("2026-01-01", BigDecimal.ZERO, BigDecimal.ZERO, 0L)));
        when(service.getRevenueByDay()).thenReturn(expected);

        assertThat(controller.getRevenueChart()).isEqualTo(expected);
    }

    @Test
    void getTopDriversDelegatesToService() {
        DriverPerformanceResponse expected = new DriverPerformanceResponse(List.of());
        when(service.getTopDrivers()).thenReturn(expected);

        assertThat(controller.getTopDrivers()).isEqualTo(expected);
    }

    @Test
    void getRecentOrdersDelegatesToService() {
        RecentOrderResponse expected = new RecentOrderResponse(List.of());
        when(service.getRecentOrders()).thenReturn(expected);

        assertThat(controller.getRecentOrders()).isEqualTo(expected);
    }

    @Test
    void getStatusDistributionDelegatesToService() {
        OrderStatusDistribution expected = new OrderStatusDistribution(Map.of());
        when(service.getStatusDistribution()).thenReturn(expected);

        assertThat(controller.getStatusDistribution()).isEqualTo(expected);
    }

    @Test
    void getOverviewDelegatesToService() {
        DashboardOverviewResponse expected = new DashboardOverviewResponse(null, null, null, null, null);
        when(service.getOverview()).thenReturn(expected);

        assertThat(controller.getOverview()).isEqualTo(expected);
    }

    @Test
    void getAllOrdersPassesStatusAndPageRequestThrough() {
        Page<OrderListItem> expected = new PageImpl<>(List.of());
        when(service.getAllOrders(OrderStatus.COMPLETED, PageRequest.of(1, 20))).thenReturn(expected);

        Page<OrderListItem> actual = controller.getAllOrders(OrderStatus.COMPLETED, 1, 20);

        assertThat(actual).isEqualTo(expected);
        verify(service).getAllOrders(OrderStatus.COMPLETED, PageRequest.of(1, 20));
    }

    @Test
    void getAllOrdersUsesDefaultPageAndSizeWhenNotProvided() {
        Page<OrderListItem> expected = new PageImpl<>(List.of());
        when(service.getAllOrders(null, PageRequest.of(0, 50))).thenReturn(expected);

        Page<OrderListItem> actual = controller.getAllOrders(null, 0, 50);

        assertThat(actual).isEqualTo(expected);
        verify(service).getAllOrders(null, PageRequest.of(0, 50));
    }

    @Test
    void getAllDriversDelegatesToServiceWithStatusFilter() {
        List<DriverListItem> expected = List.of();
        when(service.getAllDrivers(UserStatus.ACTIVE)).thenReturn(expected);

        assertThat(controller.getAllDrivers(UserStatus.ACTIVE)).isEqualTo(expected);
    }

    @Test
    void getAllDriversDelegatesToServiceWithNullFilter() {
        List<DriverListItem> expected = List.of();
        when(service.getAllDrivers(null)).thenReturn(expected);

        assertThat(controller.getAllDrivers(null)).isEqualTo(expected);
    }

    @Test
    void getAllCustomersDelegatesToServiceWithStatusFilter() {
        List<CustomerListItem> expected = List.of();
        when(service.getAllCustomers(UserStatus.PENDING_VERIFY)).thenReturn(expected);

        assertThat(controller.getAllCustomers(UserStatus.PENDING_VERIFY)).isEqualTo(expected);
    }

    @Test
    void getAllCustomersDelegatesToServiceWithNullFilter() {
        List<CustomerListItem> expected = List.of();
        when(service.getAllCustomers(null)).thenReturn(expected);

        assertThat(controller.getAllCustomers(null)).isEqualTo(expected);
    }
}
