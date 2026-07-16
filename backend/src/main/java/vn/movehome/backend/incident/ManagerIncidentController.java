package vn.movehome.backend.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;

import java.util.UUID;

/**
 * Manager xu ly su co van chuyen do tai xe bao (V44).
 * RBAC: /api/manager/** da gioi han MANAGER o SecurityConfig; them @PreAuthorize cho ro rang (HR-10).
 */
@RestController
@RequestMapping("/api/manager/incidents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerIncidentController {

    private final ManagerIncidentService service;

    @GetMapping
    public Page<IncidentListItem> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return service.list(status, page, size != null ? size : service.defaultPageSize());
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    /** Xac nhan su co -> ban don lai pool cho tai xe khac + bao khach. */
    @PostMapping("/{id}/confirm")
    public IncidentDetailResponse confirm(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id
    ) {
        return service.confirm(id, actor);
    }

    /** Qua 15 phut khong ai nhan -> hoan coc + boi thuong 200k, tru tai xe gay su co. */
    @PostMapping("/{id}/compensate")
    public IncidentDetailResponse compensate(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id
    ) {
        return service.compensate(id, actor);
    }
}
