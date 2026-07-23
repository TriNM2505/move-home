package vn.movehome.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.dto.manager.DriverRatingItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.ManagerDriverRatingService;

class ManagerDriverRatingControllerTest {

    private final ManagerDriverRatingService ratingService = mock(ManagerDriverRatingService.class);
    private final ManagerDriverRatingController controller = new ManagerDriverRatingController(ratingService);

    @Test
    void listRequiresAuthenticationThenDelegatesFilters() {
        User currentUser = User.builder().id(UUID.randomUUID()).build();
        UUID driverId = UUID.randomUUID();
        Page<DriverRatingItem> expected = Page.empty();
        when(ratingService.search(driverId, 5, "khach hang", 0, 10)).thenReturn(expected);

        Page<DriverRatingItem> actual = controller.list(currentUser, driverId, 5, "khach hang", 0, 10);

        assertThat(actual).isSameAs(expected);
        verify(ratingService).search(driverId, 5, "khach hang", 0, 10);
    }

    @Test
    void listThrowsWhenUnauthenticated() {
        assertThatThrownBy(() -> controller.list(null, null, null, null, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason())
                            .isEqualTo("AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
                });
    }
}
