package vn.movehome.backend.dispute;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import vn.movehome.backend.entity.User;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final DisputePhotoService disputePhotoService;

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

    // Khach dinh kem 1 anh bang chung cho khieu nai cua minh (toi da 3 anh; multipart, AC-10)
    @PostMapping(value = "/api/customer/disputes/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public void uploadPhoto(
            @AuthenticationPrincipal User customer,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        disputePhotoService.upload(id, customer.getId(), file);
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

    // Khau tru tai xe: tru vi ngay, thieu thi tai xe co 2 phut nop bo sung (HR-07: chi Manager/Admin)
    @PostMapping("/api/manager/disputes/{id}/resolve-deduct")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DisputeActionResponse resolveDeduct(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDeductRequest request
    ) {
        return disputeService.resolveDeduct(id, actor, request);
    }

    // Khieu nai doi chieu tai xe (DRIVER_MISMATCH): accept = hoan coc + phat 500k / reject = bac (HR-07)
    @PostMapping("/api/manager/disputes/{id}/resolve-mismatch")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public DisputeActionResponse resolveMismatch(
            @AuthenticationPrincipal User actor,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveMismatchRequest request
    ) {
        return disputeService.resolveMismatch(id, actor, request.accept(), request.note());
    }

    // Khoan phat dang cho nop cua tai xe (banner countdown) — null neu khong co
    @GetMapping("/api/driver/penalties/pending")
    @PreAuthorize("hasRole('DRIVER')")
    public DriverPenaltyResponse getPendingPenalty(@AuthenticationPrincipal User driver) {
        return disputeService.getPendingPenalty(driver.getId());
    }

    // Tai xe nop bo sung tien phat (gia lap demo — chi hoat dong khi con han va dung chu khoan phat)
    @PostMapping("/api/driver/penalties/{disputeId}/pay-mock")
    @PreAuthorize("hasRole('DRIVER')")
    public DisputeActionResponse payPenalty(
            @AuthenticationPrincipal User driver,
            @PathVariable UUID disputeId
    ) {
        return disputeService.payPenaltyMock(driver, disputeId);
    }
}
