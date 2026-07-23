package vn.movehome.backend.dispute;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import vn.movehome.backend.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisputeControllerTest {

    private final DisputeService disputeService = mock(DisputeService.class);
    private final DisputePhotoService disputePhotoService = mock(DisputePhotoService.class);
    private final DisputeController controller = new DisputeController(disputeService, disputePhotoService);

    @Test
    void createDelegatesToServiceWithOrderIdAndAuthenticatedCustomer() {
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(UUID.randomUUID()).build();
        CreateDisputeRequest request = new CreateDisputeRequest(
                "ITEM_DAMAGED", new BigDecimal("100000"), "Do dac bi vo trong qua trinh van chuyen");
        DisputeActionResponse expected = new DisputeActionResponse(
                UUID.randomUUID(), orderId, "ORD001", "COMPLETED", "OPEN",
                null, null, null, null, "Da tao khieu nai");
        when(disputeService.create(orderId, customer, request)).thenReturn(expected);

        DisputeActionResponse actual = controller.create(customer, orderId, request);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).create(orderId, customer, request);
    }

    @Test
    void listUsesProvidedSizeWhenPresent() {
        Page<DisputeListItemResponse> expected = new PageImpl<>(List.of());
        when(disputeService.list("OPEN", 1, 5)).thenReturn(expected);

        Page<DisputeListItemResponse> actual = controller.list("OPEN", 1, 5);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).list("OPEN", 1, 5);
    }

    @Test
    void listFallsBackToServiceDefaultPageSizeWhenSizeMissing() {
        Page<DisputeListItemResponse> expected = new PageImpl<>(List.of());
        when(disputeService.defaultPageSize()).thenReturn(20);
        when(disputeService.list(null, 0, 20)).thenReturn(expected);

        Page<DisputeListItemResponse> actual = controller.list(null, 0, null);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).defaultPageSize();
        verify(disputeService).list(null, 0, 20);
    }

    @Test
    void uploadPhotoDelegatesToPhotoServiceUsingAuthenticatedCustomerId() {
        UUID customerId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        MockMultipartFile file = new MockMultipartFile("file", "evidence.jpg", "image/jpeg", new byte[]{1, 2, 3});

        controller.uploadPhoto(customer, disputeId, file);

        verify(disputePhotoService).upload(disputeId, customerId, file);
    }

    @Test
    void detailDelegatesToService() {
        UUID disputeId = UUID.randomUUID();
        DisputeDetailResponse expected = new DisputeDetailResponse(
                disputeId, "OPEN", "ITEM_DAMAGED", new BigDecimal("100000"),
                "Do dac bi vo", null, null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null,
                List.of(), null, null, null);
        when(disputeService.detail(disputeId)).thenReturn(expected);

        DisputeDetailResponse actual = controller.detail(disputeId);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).detail(disputeId);
    }

    @Test
    void resolveDelegatesToServiceWithActorAndRequest() {
        UUID disputeId = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).build();
        ResolveDisputeRequest request = new ResolveDisputeRequest(
                new BigDecimal("100000"), "Chap nhan hoan tien cho khach hang");
        DisputeActionResponse expected = new DisputeActionResponse(
                disputeId, UUID.randomUUID(), "ORD001", "COMPLETED", "RESOLVED",
                new BigDecimal("100000"), "OK", actor.getId(), OffsetDateTime.now(), "Da xu ly");
        when(disputeService.resolve(disputeId, actor, request)).thenReturn(expected);

        DisputeActionResponse actual = controller.resolve(actor, disputeId, request);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).resolve(disputeId, actor, request);
    }

    @Test
    void rejectDelegatesToServiceWithActorAndRequest() {
        UUID disputeId = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).build();
        RejectDisputeRequest request = new RejectDisputeRequest("Khong du bang chung de xu ly");
        DisputeActionResponse expected = new DisputeActionResponse(
                disputeId, UUID.randomUUID(), "ORD001", "COMPLETED", "REJECTED",
                null, "Khong du bang chung de xu ly", actor.getId(), OffsetDateTime.now(), "Da bac khieu nai");
        when(disputeService.reject(disputeId, actor, request)).thenReturn(expected);

        DisputeActionResponse actual = controller.reject(actor, disputeId, request);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).reject(disputeId, actor, request);
    }

    @Test
    void resolveDeductDelegatesToServiceWithActorAndRequest() {
        UUID disputeId = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).build();
        ResolveDeductRequest request = new ResolveDeductRequest(
                new BigDecimal("50000"), "Tru vi tai xe do lam vo hang hoa cua khach");
        DisputeActionResponse expected = new DisputeActionResponse(
                disputeId, UUID.randomUUID(), "ORD001", "COMPLETED", "RESOLVED",
                new BigDecimal("50000"), request.note(), actor.getId(), OffsetDateTime.now(), "Da khau tru");
        when(disputeService.resolveDeduct(disputeId, actor, request)).thenReturn(expected);

        DisputeActionResponse actual = controller.resolveDeduct(actor, disputeId, request);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).resolveDeduct(disputeId, actor, request);
    }

    @Test
    void resolveMismatchDelegatesToServiceWithAcceptAndNote() {
        UUID disputeId = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).build();
        ResolveMismatchRequest request = new ResolveMismatchRequest(true, "Xac nhan sai tai xe");
        DisputeActionResponse expected = new DisputeActionResponse(
                disputeId, UUID.randomUUID(), "ORD001", "CANCELLED", "RESOLVED",
                null, "Xac nhan sai tai xe", actor.getId(), OffsetDateTime.now(), "Da xu ly doi chieu");
        when(disputeService.resolveMismatch(disputeId, actor, true, "Xac nhan sai tai xe")).thenReturn(expected);

        DisputeActionResponse actual = controller.resolveMismatch(actor, disputeId, request);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).resolveMismatch(disputeId, actor, true, "Xac nhan sai tai xe");
    }

    @Test
    void getPendingPenaltyDelegatesToServiceUsingAuthenticatedDriverId() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).build();
        DriverPenaltyResponse expected = new DriverPenaltyResponse(
                UUID.randomUUID(), "ORD001", new BigDecimal("500000"), OffsetDateTime.now());
        when(disputeService.getPendingPenalty(driverId)).thenReturn(expected);

        DriverPenaltyResponse actual = controller.getPendingPenalty(driver);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).getPendingPenalty(driverId);
    }

    @Test
    void payPenaltyDelegatesToServiceWithAuthenticatedDriverAndDisputeId() {
        UUID disputeId = UUID.randomUUID();
        User driver = User.builder().id(UUID.randomUUID()).build();
        DisputeActionResponse expected = new DisputeActionResponse(
                disputeId, UUID.randomUUID(), "ORD001", "COMPLETED", "RESOLVED",
                null, null, null, null, "Da nop bo sung");
        when(disputeService.payPenaltyMock(driver, disputeId)).thenReturn(expected);

        DisputeActionResponse actual = controller.payPenalty(driver, disputeId);

        assertThat(actual).isEqualTo(expected);
        verify(disputeService).payPenaltyMock(driver, disputeId);
    }
}
