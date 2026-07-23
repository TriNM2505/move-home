package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import vn.movehome.backend.dto.admin.detail.AdminOrderDetailResponse;
import vn.movehome.backend.dto.admin.detail.AuditLogItem;
import vn.movehome.backend.dto.admin.detail.CustomerDetailResponse;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.dto.admin.detail.DriverOrderItem;
import vn.movehome.backend.service.AdminDetailService;
import vn.movehome.backend.service.AdminOrderDetailService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller chi delegate sang service — test o day chi xac nhan dung tham so
 * duoc chuyen tiep va gia tri tra ve dung nhu service cung cap (khong dung MockMvc/Spring context).
 */
class AdminDetailControllerTest {

    private final AdminDetailService adminDetailService = mock(AdminDetailService.class);
    private final AdminOrderDetailService adminOrderDetailService = mock(AdminOrderDetailService.class);
    private final AdminDetailController controller =
            new AdminDetailController(adminDetailService, adminOrderDetailService);

    @Test
    void orderDetailDelegatesToAdminOrderDetailService() {
        UUID orderId = UUID.randomUUID();
        AdminOrderDetailResponse expected = new AdminOrderDetailResponse(
                null, null, null, null, null, null, List.of(), List.of());
        when(adminOrderDetailService.orderDetail(orderId)).thenReturn(expected);

        AdminOrderDetailResponse actual = controller.orderDetail(orderId);

        assertThat(actual).isSameAs(expected);
        verify(adminOrderDetailService).orderDetail(orderId);
    }

    @Test
    void driverDetailDelegatesToAdminDetailService() {
        UUID driverId = UUID.randomUUID();
        DriverDetailResponse expected = new DriverDetailResponse(
                null, null, null, List.of(), null, null, null, null,
                List.of(), List.of(), "OFFLINE", null, List.of());
        when(adminDetailService.driverDetail(driverId)).thenReturn(expected);

        DriverDetailResponse actual = controller.driverDetail(driverId);

        assertThat(actual).isSameAs(expected);
        verify(adminDetailService).driverDetail(driverId);
    }

    @Test
    void customerDetailDelegatesToAdminDetailService() {
        UUID customerId = UUID.randomUUID();
        CustomerDetailResponse expected = new CustomerDetailResponse(
                null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
        when(adminDetailService.customerDetail(customerId)).thenReturn(expected);

        CustomerDetailResponse actual = controller.customerDetail(customerId);

        assertThat(actual).isSameAs(expected);
        verify(adminDetailService).customerDetail(customerId);
    }

    @Test
    void entityAuditLogDelegatesFiltersAndPaginationToAdminDetailService() {
        UUID id = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 10);
        Page<AuditLogItem> expected = Page.empty();
        when(adminDetailService.entityAuditLog("drivers", id, "EVENT", from, to, 1, 20))
                .thenReturn(expected);

        Page<AuditLogItem> actual = controller.entityAuditLog("drivers", id, "EVENT", from, to, 1, 20);

        assertThat(actual).isSameAs(expected);
        verify(adminDetailService).entityAuditLog("drivers", id, "EVENT", from, to, 1, 20);
    }

    @Test
    void driverOrderHistoryDelegatesToAdminDetailService() {
        UUID driverId = UUID.randomUUID();
        Page<DriverOrderItem> expected = Page.empty();
        when(adminDetailService.driverOrderHistory(driverId, "ACCEPTED", 0, 20)).thenReturn(expected);

        Page<DriverOrderItem> actual = controller.driverOrderHistory(driverId, "ACCEPTED", 0, 20);

        assertThat(actual).isSameAs(expected);
        verify(adminDetailService).driverOrderHistory(driverId, "ACCEPTED", 0, 20);
    }

    @Test
    void customerOrderHistoryDelegatesToAdminDetailService() {
        UUID customerId = UUID.randomUUID();
        Page<CustomerOrderItem> expected = Page.empty();
        when(adminDetailService.customerOrderHistory(customerId, "COMPLETED", 2, 50)).thenReturn(expected);

        Page<CustomerOrderItem> actual = controller.customerOrderHistory(customerId, "COMPLETED", 2, 50);

        assertThat(actual).isSameAs(expected);
        verify(adminDetailService).customerOrderHistory(customerId, "COMPLETED", 2, 50);
    }
}
