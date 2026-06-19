package vn.movehome.backend.driver;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

@RestController
@RequestMapping("/api/driver/orders")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverOrderQueryController {

    private final DriverOrderQueryService driverOrderQueryService;

    @GetMapping("/available")
    public Page<DriverOrderListItemDTO> getAvailableOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : driverOrderQueryService.defaultPageSize();
        return driverOrderQueryService.getAvailableOrders(page, pageSize);
    }

    @GetMapping
    public Page<DriverOrderListItemDTO> getMyOrders(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int pageSize = size != null ? size : driverOrderQueryService.defaultPageSize();
        return driverOrderQueryService.getDriverOrders(currentUser.getId(), status, page, pageSize);
    }

    @GetMapping("/{id}")
    public DriverOrderDetailDTO getOrderDetail(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id
    ) {
        return driverOrderQueryService.getOrderDetail(currentUser.getId(), id);
    }
}
