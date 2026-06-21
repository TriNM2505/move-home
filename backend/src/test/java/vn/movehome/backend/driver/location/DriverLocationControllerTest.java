package vn.movehome.backend.driver.location;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverLocationControllerTest {

    private final DriverLocationService service = mock(DriverLocationService.class);
    private final DriverLocationController controller = new DriverLocationController(service);

    @Test
    void updateUsesDriverIdFromAuthenticatedPrincipal() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).build();
        UpdateDriverLocationRequest request = new UpdateDriverLocationRequest(
                new BigDecimal("21.0285110"),
                new BigDecimal("105.8048170"),
                new BigDecimal("120.50"),
                new BigDecimal("24.20"));

        controller.updateLocation(driver, request);

        verify(service).updateLocation(driverId, request);
    }

    @Test
    void getUsesCustomerIdFromAuthenticatedPrincipal() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        DriverLocationResponse expected = new DriverLocationResponse(
                UUID.randomUUID(),
                new BigDecimal("21.0285110"),
                new BigDecimal("105.8048170"),
                null,
                null,
                Instant.now(),
                false);
        when(service.getOrderLocation(customerId, orderId)).thenReturn(expected);

        DriverLocationResponse actual = controller.getOrderLocation(customer, orderId);

        assertThat(actual).isEqualTo(expected);
        verify(service).getOrderLocation(customerId, orderId);
    }
}
