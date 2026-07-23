package vn.movehome.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.dto.driver.onboarding.DriverProfileResponse;
import vn.movehome.backend.dto.driver.onboarding.UpdateDriverProfileRequest;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.DriverProfileService;

class DriverProfileControllerTest {

    private final DriverProfileService service = mock(DriverProfileService.class);
    private final DriverProfileController controller = new DriverProfileController(service);

    @Test
    void getProfileUsesAuthenticatedPrincipalId() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        DriverProfileResponse expected = buildResponse();
        when(service.getProfile(driverId)).thenReturn(expected);

        DriverProfileResponse actual = controller.getProfile(currentUser);

        assertThat(actual).isEqualTo(expected);
        verify(service).getProfile(driverId);
    }

    @Test
    void getProfileThrowsWhenUnauthenticated() {
        assertThatThrownBy(() -> controller.getProfile(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason())
                            .isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }

    @Test
    void updateProfileUsesAuthenticatedPrincipalId() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        UpdateDriverProfileRequest request = new UpdateDriverProfileRequest();
        DriverProfileResponse expected = buildResponse();
        when(service.updateProfile(driverId, request)).thenReturn(expected);

        DriverProfileResponse actual = controller.updateProfile(currentUser, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).updateProfile(driverId, request);
    }

    @Test
    void updateProfileThrowsWhenUnauthenticated() {
        UpdateDriverProfileRequest request = new UpdateDriverProfileRequest();

        assertThatThrownBy(() -> controller.updateProfile(null, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void submitProfileUsesAuthenticatedPrincipalId() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        DriverProfileResponse expected = buildResponse();
        when(service.submitProfile(driverId)).thenReturn(expected);

        DriverProfileResponse actual = controller.submitProfile(currentUser);

        assertThat(actual).isEqualTo(expected);
        verify(service).submitProfile(driverId);
    }

    @Test
    void submitProfileThrowsWhenUnauthenticated() {
        assertThatThrownBy(() -> controller.submitProfile(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private DriverProfileResponse buildResponse() {
        return new DriverProfileResponse(
                "B123456789",
                "B2",
                LocalDate.now().plusDays(100),
                "30E-56789",
                "TRUCK_500KG",
                500,
                null,
                0,
                new BigDecimal("5.00"),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "CHUA_NOP");
    }
}
