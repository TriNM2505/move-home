package vn.movehome.backend.driver;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverOrderQueryControllerTest {

    private final DriverOrderQueryService driverOrderQueryService = mock(DriverOrderQueryService.class);
    private final DriverOrderQueryController controller = new DriverOrderQueryController(driverOrderQueryService);

    private final UUID driverId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final User currentUser = User.builder().id(driverId).role(UserRole.DRIVER).build();

    @Test
    void getAvailableOrders_usesProvidedSize() {
        Page<DriverOrderListItemDTO> page = new PageImpl<>(List.of());
        when(driverOrderQueryService.getAvailableOrders(0, 25)).thenReturn(page);

        Page<DriverOrderListItemDTO> result = controller.getAvailableOrders(0, 25);

        assertThat(result).isSameAs(page);
        verify(driverOrderQueryService).getAvailableOrders(0, 25);
    }

    @Test
    void getAvailableOrders_usesDefaultSizeWhenSizeNull() {
        when(driverOrderQueryService.defaultPageSize()).thenReturn(10);
        Page<DriverOrderListItemDTO> page = new PageImpl<>(List.of());
        when(driverOrderQueryService.getAvailableOrders(0, 10)).thenReturn(page);

        Page<DriverOrderListItemDTO> result = controller.getAvailableOrders(0, null);

        assertThat(result).isSameAs(page);
        verify(driverOrderQueryService).getAvailableOrders(0, 10);
    }

    @Test
    void getMyOrders_usesProvidedSizeAndPrincipalId() {
        Page<DriverOrderListItemDTO> page = new PageImpl<>(List.of());
        when(driverOrderQueryService.getDriverOrders(driverId, "ACCEPTED", 0, 25)).thenReturn(page);

        Page<DriverOrderListItemDTO> result = controller.getMyOrders(currentUser, "ACCEPTED", 0, 25);

        assertThat(result).isSameAs(page);
        verify(driverOrderQueryService).getDriverOrders(driverId, "ACCEPTED", 0, 25);
    }

    @Test
    void getMyOrders_usesDefaultSizeWhenSizeNull() {
        when(driverOrderQueryService.defaultPageSize()).thenReturn(10);
        Page<DriverOrderListItemDTO> page = new PageImpl<>(List.of());
        when(driverOrderQueryService.getDriverOrders(driverId, null, 0, 10)).thenReturn(page);

        Page<DriverOrderListItemDTO> result = controller.getMyOrders(currentUser, null, 0, null);

        assertThat(result).isSameAs(page);
        verify(driverOrderQueryService).getDriverOrders(driverId, null, 0, 10);
    }

    @Test
    void getOrderDetail_delegatesToServiceWithPrincipalId() {
        DriverOrderDetailDTO dto = new DriverOrderDetailDTO(
                orderId, "MH-0001", "CONFIRMED", true, false, false,
                null, null, "TRUCK_500KG", 0,
                "123 Đường A", "Cầu Giấy", null, null,
                "456 Đường B", "Đống Đa", null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null,
                null, null);
        when(driverOrderQueryService.getOrderDetail(driverId, orderId)).thenReturn(dto);

        DriverOrderDetailDTO result = controller.getOrderDetail(currentUser, orderId);

        assertThat(result).isSameAs(dto);
        verify(driverOrderQueryService).getOrderDetail(driverId, orderId);
    }
}
