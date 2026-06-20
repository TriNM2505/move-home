package vn.movehome.backend.driver;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

@RestController
@RequestMapping("/api/driver/orders")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverOrderController {

    private final DriverOrderService driverOrderService;

    @PostMapping("/{id}/accept")
    public ResponseEntity<Void> acceptOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.acceptOrder(currentUser.getId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> startOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.startOrder(currentUser.getId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completeOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.completeOrder(currentUser.getId(), id);
        return ResponseEntity.ok().build();
    }
}
