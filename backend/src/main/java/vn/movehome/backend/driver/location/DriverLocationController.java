package vn.movehome.backend.driver.location;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DriverLocationController {

    private final DriverLocationService driverLocationService;

    @PostMapping("/api/driver/location")
    @PreAuthorize("hasRole('DRIVER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(
            @AuthenticationPrincipal User driver,
            @Valid @RequestBody UpdateDriverLocationRequest request) {
        driverLocationService.updateLocation(driver.getId(), request);
    }

    @GetMapping("/api/customer/orders/{orderId}/location")
    @PreAuthorize("hasRole('CUSTOMER')")
    public DriverLocationResponse getOrderLocation(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID orderId) {
        return driverLocationService.getOrderLocation(customer.getId(), orderId);
    }
}
