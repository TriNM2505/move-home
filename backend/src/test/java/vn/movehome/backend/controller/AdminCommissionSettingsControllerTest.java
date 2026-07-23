package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.dto.admin.finance.CommissionSettingsResponse;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsRequest;
import vn.movehome.backend.dto.admin.finance.UpdateCommissionSettingsResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.service.CommissionSettingsService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCommissionSettingsControllerTest {

    private final CommissionSettingsService service = mock(CommissionSettingsService.class);
    private final AdminCommissionSettingsController controller = new AdminCommissionSettingsController(service);

    @Test
    void getCurrentDelegatesToService() {
        CommissionSettingsResponse expected = new CommissionSettingsResponse(
                3L, new BigDecimal("0.3000"), OffsetDateTime.now(), UUID.randomUUID());
        when(service.getCurrent()).thenReturn(expected);

        CommissionSettingsResponse actual = controller.getCurrent();

        assertThat(actual).isEqualTo(expected);
        verify(service).getCurrent();
    }

    @Test
    void updateDelegatesToService() {
        User admin = User.builder().id(UUID.randomUUID()).role(UserRole.ADMIN).build();
        UpdateCommissionSettingsRequest request = new UpdateCommissionSettingsRequest(3L, new BigDecimal("0.2500"));
        UpdateCommissionSettingsResponse expected = new UpdateCommissionSettingsResponse(
                "Da cap nhat cau hinh",
                new CommissionSettingsResponse(4L, new BigDecimal("0.2500"), OffsetDateTime.now(), admin.getId()),
                OffsetDateTime.now(),
                4L);
        when(service.update(admin, request)).thenReturn(expected);

        UpdateCommissionSettingsResponse actual = controller.update(admin, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).update(admin, request);
    }
}
