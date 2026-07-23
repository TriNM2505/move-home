package vn.movehome.backend.driver;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverOrderControllerTest {

    private final DriverOrderService driverOrderService = mock(DriverOrderService.class);
    private final DriverOrderController controller = new DriverOrderController(driverOrderService);

    private final UUID driverId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final User currentUser = User.builder().id(driverId).role(UserRole.DRIVER).build();

    @Test
    void acceptOrder_delegatesToServiceWithPrincipalIdAndRole() {
        ResponseEntity<Void> response = controller.acceptOrder(currentUser, orderId);

        verify(driverOrderService).acceptOrder(driverId, "DRIVER", orderId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void markArrived_delegatesToServiceWithPrincipalIdAndRole() {
        ResponseEntity<Void> response = controller.markArrived(currentUser, orderId);

        verify(driverOrderService).markArrived(driverId, "DRIVER", orderId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelNoShow_delegatesToServiceWithPrincipalIdAndRole() {
        ResponseEntity<Void> response = controller.cancelNoShow(currentUser, orderId);

        verify(driverOrderService).cancelNoShow(driverId, "DRIVER", orderId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requestFinalPayment_delegatesToServiceWithPrincipalIdAndRole() {
        ResponseEntity<Void> response = controller.requestFinalPayment(currentUser, orderId);

        verify(driverOrderService).requestFinalPayment(driverId, "DRIVER", orderId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void completeOrder_delegatesToServiceWithPrincipalIdAndRole() {
        ResponseEntity<Void> response = controller.completeOrder(currentUser, orderId);

        verify(driverOrderService).completeOrder(driverId, "DRIVER", orderId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
