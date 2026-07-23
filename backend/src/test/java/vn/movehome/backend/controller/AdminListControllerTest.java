package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import vn.movehome.backend.dto.admin.list.CustomerListItem;
import vn.movehome.backend.dto.admin.list.DriverListItem;
import vn.movehome.backend.dto.admin.list.OrderListItem;
import vn.movehome.backend.dto.admin.list.WithdrawalListItem;
import vn.movehome.backend.service.AdminListService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller chi delegate sang AdminListService — test o day chi xac nhan dung tham so
 * duoc chuyen tiep (khong dung MockMvc/Spring context).
 */
class AdminListControllerTest {

    private final AdminListService adminListService = mock(AdminListService.class);
    private final AdminListController controller = new AdminListController(adminListService);

    @Test
    void listOrdersDelegatesAllFiltersToService() {
        Page<OrderListItem> expected = Page.empty();
        when(adminListService.listOrders(
                "COMPLETED", "MH-1", "2026-06-01", "2026-06-10", 1, 20, "total_quote,asc"))
                .thenReturn(expected);

        Page<OrderListItem> actual = controller.listOrders(
                1, 20, "COMPLETED", "MH-1", "2026-06-01", "2026-06-10", "total_quote,asc");

        assertThat(actual).isSameAs(expected);
        verify(adminListService).listOrders(
                "COMPLETED", "MH-1", "2026-06-01", "2026-06-10", 1, 20, "total_quote,asc");
    }

    @Test
    void listDriversDelegatesFiltersToService() {
        Page<DriverListItem> expected = Page.empty();
        when(adminListService.listDrivers("ACTIVE", "Driver One", 0, 20, "average_rating,desc"))
                .thenReturn(expected);

        Page<DriverListItem> actual = controller.listDrivers(
                0, 20, "ACTIVE", "Driver One", "average_rating,desc");

        assertThat(actual).isSameAs(expected);
        verify(adminListService).listDrivers("ACTIVE", "Driver One", 0, 20, "average_rating,desc");
    }

    @Test
    void listCustomersDelegatesFiltersToService() {
        Page<CustomerListItem> expected = Page.empty();
        when(adminListService.listCustomers("LOCKED", "customer@movehome.vn", 2, 50, "total_spent,desc"))
                .thenReturn(expected);

        Page<CustomerListItem> actual = controller.listCustomers(
                2, 50, "LOCKED", "customer@movehome.vn", "total_spent,desc");

        assertThat(actual).isSameAs(expected);
        verify(adminListService).listCustomers("LOCKED", "customer@movehome.vn", 2, 50, "total_spent,desc");
    }

    @Test
    void listWithdrawalsDelegatesAllFiltersToService() {
        Page<WithdrawalListItem> expected = Page.empty();
        when(adminListService.listWithdrawals(
                "PROCESSED", "TXN-1", "2026-06-01", "2026-06-10", 0, 10, "amount,desc"))
                .thenReturn(expected);

        Page<WithdrawalListItem> actual = controller.listWithdrawals(
                0, 10, "PROCESSED", "TXN-1", "2026-06-01", "2026-06-10", "amount,desc");

        assertThat(actual).isSameAs(expected);
        verify(adminListService).listWithdrawals(
                "PROCESSED", "TXN-1", "2026-06-01", "2026-06-10", 0, 10, "amount,desc");
    }
}
