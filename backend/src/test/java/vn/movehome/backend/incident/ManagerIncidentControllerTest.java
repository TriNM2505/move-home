package vn.movehome.backend.incident;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import vn.movehome.backend.entity.User;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiem tra ManagerIncidentController (V44) theo pattern DriverDocumentControllerTest:
 * khoi tao controller thuan voi mock service, khong load Spring context.
 */
class ManagerIncidentControllerTest {

    private final ManagerIncidentService service = mock(ManagerIncidentService.class);
    private final ManagerIncidentController controller = new ManagerIncidentController(service);

    @Test
    void list_whenSizeProvided_passesSizeDirectlyWithoutUsingDefault() {
        Page<IncidentListItem> expected = new PageImpl<>(List.of());
        when(service.list("PENDING", 0, 50)).thenReturn(expected);

        Page<IncidentListItem> result = controller.list("PENDING", 0, 50);

        assertThat(result).isSameAs(expected);
        verify(service, never()).defaultPageSize();
    }

    @Test
    void list_whenSizeNull_usesServiceDefaultPageSize() {
        Page<IncidentListItem> expected = new PageImpl<>(List.of());
        when(service.defaultPageSize()).thenReturn(20);
        when(service.list(null, 0, 20)).thenReturn(expected);

        Page<IncidentListItem> result = controller.list(null, 0, null);

        assertThat(result).isSameAs(expected);
        verify(service).defaultPageSize();
        verify(service).list(null, 0, 20);
    }

    @Test
    void detail_delegatesToService() {
        UUID incidentId = UUID.randomUUID();
        IncidentDetailResponse expected = new IncidentDetailResponse(
                incidentId, UUID.randomUUID(), "MH-001", "CONFIRMED", "IN_PROGRESS",
                UUID.randomUUID(), "Tai xe", "0900000000", UUID.randomUUID(), "Khach", "0911111111",
                "Hong xe", "CONFIRMED", null, false, null, null, null, null, null, null, List.of(), null);
        when(service.detail(incidentId)).thenReturn(expected);

        IncidentDetailResponse result = controller.detail(incidentId);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void confirm_delegatesToServiceWithAuthenticatedActor() {
        UUID incidentId = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).build();
        IncidentDetailResponse expected = sampleDetail(incidentId);
        when(service.confirm(incidentId, actor)).thenReturn(expected);

        IncidentDetailResponse result = controller.confirm(actor, incidentId);

        assertThat(result).isSameAs(expected);
        verify(service).confirm(incidentId, actor);
    }

    @Test
    void compensate_delegatesToServiceWithAuthenticatedActor() {
        UUID incidentId = UUID.randomUUID();
        User actor = User.builder().id(UUID.randomUUID()).build();
        IncidentDetailResponse expected = sampleDetail(incidentId);
        when(service.compensate(incidentId, actor)).thenReturn(expected);

        IncidentDetailResponse result = controller.compensate(actor, incidentId);

        assertThat(result).isSameAs(expected);
        verify(service).compensate(incidentId, actor);
    }

    private IncidentDetailResponse sampleDetail(UUID incidentId) {
        return new IncidentDetailResponse(
                incidentId, UUID.randomUUID(), "MH-001", "CONFIRMED", "IN_PROGRESS",
                UUID.randomUUID(), "Tai xe", "0900000000", UUID.randomUUID(), "Khach", "0911111111",
                "Hong xe", "CONFIRMED", null, false, null, null, null, null, null, null, List.of(), null);
    }
}
