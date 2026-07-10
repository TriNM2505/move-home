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
        driverOrderService.acceptOrder(currentUser.getId(), currentUser.getRole().name(), id);
        return ResponseEntity.ok().build();
    }

    // "Da den diem don": ghi arrived_at, don van ACCEPTED, cho khach doi chieu (khong chuyen IN_PROGRESS)
    @PostMapping("/{id}/start")
    public ResponseEntity<Void> markArrived(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.markArrived(currentUser.getId(), currentUser.getRole().name(), id);
        return ResponseEntity.ok().build();
    }

    // "Khach khong ra": huy don sau N phut cho o diem don; coc thanh thu nhap tai xe
    @PostMapping("/{id}/cancel-no-show")
    public ResponseEntity<Void> cancelNoShow(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.cancelNoShow(currentUser.getId(), currentUser.getRole().name(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/request-final-payment")
    public ResponseEntity<Void> requestFinalPayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.requestFinalPayment(currentUser.getId(), currentUser.getRole().name(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completeOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        driverOrderService.completeOrder(currentUser.getId(), currentUser.getRole().name(), id);
        return ResponseEntity.ok().build();
    }
}
