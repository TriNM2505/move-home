package vn.movehome.backend.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CustomerOrderActionService customerOrderActionService;

    @PostMapping("/api/customer/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(
            @AuthenticationPrincipal User customer,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(customer.getId(), request);
    }

    @PutMapping("/api/customer/orders/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CancelOrderResponse cancelOrder(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id,
            @RequestBody CancelOrderRequest request) {
        return customerOrderActionService.cancelOrder(
                customer.getId(), customer.getRole().name(), id, request);
    }

    @PostMapping("/api/customer/orders/{id}/rating")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse rateOrder(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id,
            @RequestBody RatingRequest request) {
        return customerOrderActionService.rateOrder(customer.getId(), id, request);
    }
}
