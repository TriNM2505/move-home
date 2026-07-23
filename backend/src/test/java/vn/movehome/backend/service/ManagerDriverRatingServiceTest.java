package vn.movehome.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.dto.manager.DriverRatingItem;
import vn.movehome.backend.order.OrderRatingRepository;

@ExtendWith(MockitoExtension.class)
class ManagerDriverRatingServiceTest {

    @Mock
    private OrderRatingRepository orderRatingRepository;

    @InjectMocks
    private ManagerDriverRatingService ratingService;

    @Test
    void searchPassesConvertedFiltersAndPageableToRepository() {
        UUID driverId = UUID.randomUUID();
        DriverRatingItem item = buildItem(driverId);
        Page<DriverRatingItem> page = new PageImpl<>(List.of(item));
        when(orderRatingRepository.searchForManager(any(), any(), any(), any())).thenReturn(page);

        Page<DriverRatingItem> result = ratingService.search(driverId, 5, "  Nguyen Van A  ", 0, 10);

        assertThat(result.getContent()).containsExactly(item);
        ArgumentCaptor<String> driverIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> starsCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRatingRepository).searchForManager(
                driverIdCaptor.capture(), starsCaptor.capture(), keywordCaptor.capture(), pageableCaptor.capture());

        assertThat(driverIdCaptor.getValue()).isEqualTo(driverId.toString());
        assertThat(starsCaptor.getValue()).isEqualTo("5");
        assertThat(keywordCaptor.getValue()).isEqualTo("%nguyen van a%");
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void searchPassesNullFiltersWhenDriverStarsAndKeywordAreAbsent() {
        when(orderRatingRepository.searchForManager(any(), any(), any(), any())).thenReturn(Page.empty());

        ratingService.search(null, null, null, 0, 10);

        ArgumentCaptor<String> driverIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> starsCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderRatingRepository).searchForManager(
                driverIdCaptor.capture(), starsCaptor.capture(), keywordCaptor.capture(), any());

        assertThat(driverIdCaptor.getValue()).isNull();
        assertThat(starsCaptor.getValue()).isNull();
        assertThat(keywordCaptor.getValue()).isNull();
    }

    @Test
    void searchTreatsBlankKeywordAsNoFilter() {
        when(orderRatingRepository.searchForManager(any(), any(), any(), any())).thenReturn(Page.empty());

        ratingService.search(null, null, "   ", 0, 10);

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderRatingRepository).searchForManager(any(), any(), keywordCaptor.capture(), any());
        assertThat(keywordCaptor.getValue()).isNull();
    }

    @Test
    void searchRejectsStarsBelowMinimum() {
        assertThatThrownBy(() -> ratingService.search(null, 0, null, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).isEqualTo("VALIDATION_ERROR|Bộ lọc số sao phải từ 1 đến 5.");
                });
        verify(orderRatingRepository, never()).searchForManager(any(), any(), any(), any());
    }

    @Test
    void searchRejectsStarsAboveMaximum() {
        assertThatThrownBy(() -> ratingService.search(null, 6, null, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getReason()).isEqualTo("VALIDATION_ERROR|Bộ lọc số sao phải từ 1 đến 5.");
                });
        verify(orderRatingRepository, never()).searchForManager(any(), any(), any(), any());
    }

    @Test
    void searchClampsNegativePageToZero() {
        when(orderRatingRepository.searchForManager(any(), any(), any(), any())).thenReturn(Page.empty());

        ratingService.search(null, null, null, -3, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRatingRepository).searchForManager(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    @Test
    void searchClampsSizeBelowOneToDefaultTen() {
        when(orderRatingRepository.searchForManager(any(), any(), any(), any())).thenReturn(Page.empty());

        ratingService.search(null, null, null, 0, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRatingRepository).searchForManager(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void searchClampsSizeAboveMaxToOneHundred() {
        when(orderRatingRepository.searchForManager(any(), any(), any(), any())).thenReturn(Page.empty());

        ratingService.search(null, null, null, 0, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRatingRepository).searchForManager(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    private DriverRatingItem buildItem(UUID driverId) {
        return new DriverRatingItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MH-000123",
                driverId,
                "Nguyen Van Driver",
                "Tran Thi Khach",
                5,
                "Tai xe rat nhiet tinh",
                OffsetDateTime.now());
    }
}
