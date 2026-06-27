package vn.movehome.backend.dispute;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping("/api/customer/orders/{orderId}/disputes")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeActionResponse create(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateDisputeRequest request
    ) {
        return disputeService.create(orderId, customer, request);
    }

    @GetMapping("/api/manager/disputes")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public Page<DisputeListItemResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return disputeService.list(status, page, size != null ? size : disputeService.defaultPageSize());
    }

    @GetMapping("/api/manager/disputes/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DisputeDetailResponse detail(@PathVariable UUID id) {
        return disputeService.detail(id);
    }

    @PostMapping("/api/manager/disputes/{id}/resolve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DisputeActionResponse resolve(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        return disputeService.resolve(id, actor, request);
    }

    @PostMapping("/api/manager/disputes/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DisputeActionResponse reject(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @Valid @RequestBody RejectDisputeRequest request
    ) {
        return disputeService.reject(id, actor, request);
    }
}
