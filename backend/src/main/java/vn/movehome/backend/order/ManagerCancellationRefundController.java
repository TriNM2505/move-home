package vn.movehome.backend.order;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

/**
 * Manager duyet yeu cau hoan coc do khach huy don (CONFIRMED, chua co tai xe).
 * RBAC: /api/manager/** da gioi han MANAGER o SecurityConfig; them @PreAuthorize cho ro rang (HR-10).
 */
@RestController
@RequestMapping("/api/manager/cancellation-refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerCancellationRefundController {

    private final ManagerCancellationRefundService service;

    @GetMapping
    public Page<CancellationRefundListItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return service.list(status, page, size != null ? size : service.defaultPageSize());
    }

    @GetMapping("/{id}")
    public CancellationRefundDetailResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/refund")
    public CancellationRefundDetailResponse refund(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id
    ) {
        return service.refund(id, actor);
    }

    @PostMapping("/{id}/reject")
    public CancellationRefundDetailResponse reject(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @RequestBody RejectCancellationRequest request
    ) {
        return service.reject(id, actor, request != null ? request.reason() : null);
    }
}
