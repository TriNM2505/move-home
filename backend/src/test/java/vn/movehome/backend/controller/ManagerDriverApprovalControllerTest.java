package vn.movehome.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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

class ManagerDriverApprovalControllerTest {

    private final ManagerDriverQueryService queryService = mock(ManagerDriverQueryService.class);
    private final ManagerDriverDecisionService decisionService = mock(ManagerDriverDecisionService.class);
    private final ManagerDriverApprovalController controller =
            new ManagerDriverApprovalController(queryService, decisionService);

    private final User manager = User.builder().id(UUID.randomUUID()).build();

    @Test
    void listPendingApprovalRequiresManagerThenDelegates() {
        Page<PendingDriverItem> expected = Page.empty();
        when(queryService.findPendingApproval(0, 10)).thenReturn(expected);

        Page<PendingDriverItem> actual = controller.listPendingApproval(manager, 0, 10);

        assertThat(actual).isSameAs(expected);
        verify(queryService).findPendingApproval(0, 10);
    }

    @Test
    void listPendingApprovalThrowsWhenUnauthenticated() {
        assertThatThrownBy(() -> controller.listPendingApproval(null, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason())
                            .isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }

    @Test
    void listRejectedRequiresManagerThenDelegates() {
        Page<RejectedDriverItem> expected = Page.empty();
        when(queryService.findRejected(0, 10)).thenReturn(expected);

        Page<RejectedDriverItem> actual = controller.listRejected(manager, 0, 10);

        assertThat(actual).isSameAs(expected);
        verify(queryService).findRejected(0, 10);
    }

    @Test
    void listRejectedThrowsWhenUnauthenticated() {
        assertThatThrownBy(() -> controller.listRejected(null, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getDetailRequiresManagerThenDelegates() {
        UUID driverId = UUID.randomUUID();
        DriverDetailResponse expected = buildDetailResponse(driverId);
        when(queryService.getDetail(driverId)).thenReturn(expected);

        DriverDetailResponse actual = controller.getDetail(manager, driverId);

        assertThat(actual).isSameAs(expected);
        verify(queryService).getDetail(driverId);
    }

    @Test
    void getDetailThrowsWhenUnauthenticated() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.getDetail(null, driverId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getDocumentsRequiresManagerThenDelegates() {
        UUID driverId = UUID.randomUUID();
        List<DriverDocumentItem> expected = List.of(new DriverDocumentItem(
                UUID.randomUUID(), "FACE_PHOTO", "https://cdn/x.jpg", OffsetDateTime.now()));
        when(queryService.getDocuments(driverId)).thenReturn(expected);

        List<DriverDocumentItem> actual = controller.getDocuments(manager, driverId);

        assertThat(actual).isEqualTo(expected);
        verify(queryService).getDocuments(driverId);
    }

    @Test
    void getDocumentsThrowsWhenUnauthenticated() {
        UUID driverId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.getDocuments(null, driverId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void approveRequiresManagerThenDelegatesActor() {
        UUID driverId = UUID.randomUUID();
        ApproveDriverRequest request = new ApproveDriverRequest("OK");
        ApproveDriverResponse expected = new ApproveDriverResponse(
                driverId, "ACTIVE", "Đã duyệt tài xế thành công", OffsetDateTime.now());
        when(decisionService.approve(driverId, request, manager)).thenReturn(expected);

        ApproveDriverResponse actual = controller.approve(manager, driverId, request);

        assertThat(actual).isEqualTo(expected);
        verify(decisionService).approve(driverId, request, manager);
    }

    @Test
    void approveThrowsWhenUnauthenticated() {
        UUID driverId = UUID.randomUUID();
        ApproveDriverRequest request = new ApproveDriverRequest("OK");

        assertThatThrownBy(() -> controller.approve(null, driverId, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectRequiresManagerThenDelegatesActor() {
        UUID driverId = UUID.randomUUID();
        RejectDriverRequest request = new RejectDriverRequest("Ly do khong hop le ro rang", "note");
        RejectDriverResponse expected =
                new RejectDriverResponse(driverId, "REJECTED", "Đã từ chối hồ sơ tài xế", true);
        when(decisionService.reject(driverId, request, manager)).thenReturn(expected);

        RejectDriverResponse actual = controller.reject(manager, driverId, request);

        assertThat(actual).isEqualTo(expected);
        verify(decisionService).reject(driverId, request, manager);
    }

    @Test
    void rejectThrowsWhenUnauthenticated() {
        UUID driverId = UUID.randomUUID();
        RejectDriverRequest request = new RejectDriverRequest("Ly do khong hop le ro rang", "note");

        assertThatThrownBy(() -> controller.reject(null, driverId, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private DriverDetailResponse buildDetailResponse(UUID driverId) {
        return new DriverDetailResponse(
                driverId,
                "Nguyen Van Driver",
                "driver@example.com",
                "+84901234567",
                "ACTIVE",
                "B123456789",
                "B2",
                LocalDate.now().plusDays(100),
                "30E-56789",
                "TRUCK_500KG",
                500,
                new BigDecimal("3000000"),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                UUID.randomUUID(),
                null,
                8L);
    }
}
