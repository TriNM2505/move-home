package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.dto.manager.ApproveDriverRequest;
import vn.movehome.backend.dto.manager.ApproveDriverResponse;
import vn.movehome.backend.dto.manager.DriverDetailResponse;
import vn.movehome.backend.dto.manager.DriverDocumentItem;
import vn.movehome.backend.dto.manager.PendingDriverItem;
import vn.movehome.backend.dto.manager.RejectDriverRequest;
import vn.movehome.backend.dto.manager.RejectDriverResponse;
import vn.movehome.backend.dto.manager.RejectedDriverItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.ManagerDriverDecisionService;
import vn.movehome.backend.service.ManagerDriverQueryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager/drivers")
@RequiredArgsConstructor
public class ManagerDriverApprovalController {

    private final ManagerDriverQueryService queryService;
    private final ManagerDriverDecisionService decisionService;

    @GetMapping("/pending-approval")
    public Page<PendingDriverItem> listPendingApproval(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        requireManager(currentUser);
        return queryService.findPendingApproval(page, size);
    }

    @GetMapping("/rejected")
    public Page<RejectedDriverItem> listRejected(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        requireManager(currentUser);
        return queryService.findRejected(page, size);
    }

    @GetMapping("/{driverId}")
    public DriverDetailResponse getDetail(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID driverId
    ) {
        requireManager(currentUser);
        return queryService.getDetail(driverId);
    }

    @GetMapping("/{driverId}/documents")
    public List<DriverDocumentItem> getDocuments(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID driverId
    ) {
        requireManager(currentUser);
        return queryService.getDocuments(driverId);
    }

    @PostMapping("/{driverId}/approve")
    public ApproveDriverResponse approve(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID driverId,
            @RequestBody(required = false) ApproveDriverRequest request
    ) {
        User actor = requireManager(currentUser);
        return decisionService.approve(driverId, request, actor);
    }

    @PostMapping("/{driverId}/reject")
    public RejectDriverResponse reject(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID driverId,
            @RequestBody RejectDriverRequest request
    ) {
        User actor = requireManager(currentUser);
        return decisionService.reject(driverId, request, actor);
    }

    private User requireManager(User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }

        return currentUser;
    }
}
