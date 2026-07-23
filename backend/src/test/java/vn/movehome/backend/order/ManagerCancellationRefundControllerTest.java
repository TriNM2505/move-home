package vn.movehome.backend.order;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.movehome.backend.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagerCancellationRefundControllerTest {

    private final ManagerCancellationRefundService service = mock(ManagerCancellationRefundService.class);
    private final ManagerCancellationRefundController controller = new ManagerCancellationRefundController(service);

    @Test
    void listDelegatesToServiceWithGivenParameters() {
        Page<CancellationRefundListItem> expected = new PageImpl<>(List.of(new CancellationRefundListItem(
                UUID.randomUUID(), UUID.randomUUID(), "MH0001", UUID.randomUUID(), "Nguyen Van A",
                "Doi y", "PENDING", new BigDecimal("300000"), null,
                OffsetDateTime.now(), null)));
        when(service.list("PENDING", 1, 10)).thenReturn(expected);

        Page<CancellationRefundListItem> actual = controller.list("PENDING", 1, 10);

        assertThat(actual).isSameAs(expected);
        verify(service).list("PENDING", 1, 10);
    }

    @Test
    void listUsesServiceDefaultPageSizeWhenSizeParamIsNull() {
        Page<CancellationRefundListItem> expected = new PageImpl<>(List.of());
        when(service.defaultPageSize()).thenReturn(20);
        when(service.list(null, 0, 20)).thenReturn(expected);

        Page<CancellationRefundListItem> actual = controller.list(null, 0, null);

        assertThat(actual).isSameAs(expected);
        verify(service).defaultPageSize();
        verify(service).list(null, 0, 20);
    }

    @Test
    void detailDelegatesToServiceById() {
        UUID id = UUID.randomUUID();
        CancellationRefundDetailResponse expected = new CancellationRefundDetailResponse(
                id, UUID.randomUUID(), "MH0002", "CANCELLED", UUID.randomUUID(), "Tran Thi B",
                "+84900000000", "Doi y", "PENDING", new BigDecimal("300000"), null, null,
                null, null, OffsetDateTime.now(), List.of(), null);
        when(service.detail(id)).thenReturn(expected);

        CancellationRefundDetailResponse actual = controller.detail(id);

        assertThat(actual).isSameAs(expected);
        verify(service).detail(id);
    }

    @Test
    void refundDelegatesToServiceWithAuthenticatedActor() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();
        CancellationRefundDetailResponse expected = new CancellationRefundDetailResponse(
                id, UUID.randomUUID(), "MH0003", "CANCELLED", UUID.randomUUID(), "Le Van C",
                "+84900000001", "Doi y", "REFUNDED", new BigDecimal("300000"), new BigDecimal("300000"),
                null, actor.getId(), OffsetDateTime.now(), OffsetDateTime.now(), List.of(), null);
        when(service.refund(id, actor)).thenReturn(expected);

        CancellationRefundDetailResponse actual = controller.refund(actor, id);

        assertThat(actual).isSameAs(expected);
        verify(service).refund(id, actor);
    }

    @Test
    void rejectDelegatesToServiceWithReasonFromRequestBody() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();
        RejectCancellationRequest request = new RejectCancellationRequest("Khong du dieu kien");
        CancellationRefundDetailResponse expected = new CancellationRefundDetailResponse(
                id, UUID.randomUUID(), "MH0004", "CANCELLED", UUID.randomUUID(), "Pham Thi D",
                "+84900000002", "Doi y", "REJECTED", new BigDecimal("300000"), null,
                "Khong du dieu kien", actor.getId(), OffsetDateTime.now(), OffsetDateTime.now(), List.of(), null);
        when(service.reject(id, actor, "Khong du dieu kien")).thenReturn(expected);

        CancellationRefundDetailResponse actual = controller.reject(actor, id, request);

        assertThat(actual).isSameAs(expected);
        verify(service).reject(id, actor, "Khong du dieu kien");
    }

    @Test
    void rejectPassesNullReasonWhenRequestBodyIsNull() {
        UUID id = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).email("manager@movehome.vn").build();
        CancellationRefundDetailResponse expected = new CancellationRefundDetailResponse(
                id, UUID.randomUUID(), "MH0005", "CANCELLED", UUID.randomUUID(), "Hoang Van E",
                "+84900000003", "Doi y", "PENDING", new BigDecimal("300000"), null,
                null, null, null, OffsetDateTime.now(), List.of(), null);
        when(service.reject(id, actor, null)).thenReturn(expected);

        CancellationRefundDetailResponse actual = controller.reject(actor, id, null);

        assertThat(actual).isSameAs(expected);
        verify(service).reject(id, actor, null);
    }
}
