package vn.movehome.backend.driver.finance;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.movehome.backend.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverWalletControllerTest {

    private final DriverEarningService service = mock(DriverEarningService.class);
    private final DriverWalletController controller = new DriverWalletController(service);

    @Test
    void getWalletUsesAuthenticatedPrincipalId() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        DriverWalletSummaryResponse expected = new DriverWalletSummaryResponse(
                new BigDecimal("100000"), new BigDecimal("300000"), new BigDecimal("200000"));
        when(service.getWallet(driverId)).thenReturn(expected);

        DriverWalletSummaryResponse actual = controller.getWallet(currentUser);

        assertThat(actual).isEqualTo(expected);
        verify(service).getWallet(driverId);
    }

    @Test
    void getEarningsUsesRequestedPageAndSizeWhenProvided() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        Page<DriverEarningResponse> expected = new PageImpl<>(List.of(new DriverEarningResponse(
                UUID.randomUUID(), new BigDecimal("700000"), UUID.randomUUID(), "MH1", "Thu nhập", OffsetDateTime.now())));
        when(service.getEarnings(driverId, 2, 10)).thenReturn(expected);

        Page<DriverEarningResponse> actual = controller.getEarnings(currentUser, 2, 10);

        assertThat(actual).isEqualTo(expected);
        verify(service).getEarnings(driverId, 2, 10);
    }

    @Test
    void getEarningsFallsBackToServiceDefaultPageSizeWhenSizeIsNull() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        when(service.defaultPageSize()).thenReturn(20);
        Page<DriverEarningResponse> expected = new PageImpl<>(List.of());
        when(service.getEarnings(driverId, 0, 20)).thenReturn(expected);

        Page<DriverEarningResponse> actual = controller.getEarnings(currentUser, 0, null);

        assertThat(actual).isEqualTo(expected);
        verify(service).getEarnings(driverId, 0, 20);
    }

    @Test
    void createWithdrawalReturnsCreatedStatusWithBody() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        CreateWithdrawalRequest request = new CreateWithdrawalRequest(new BigDecimal("300000"), "VCB", "0123456789");
        WithdrawalRequestResponse expected = new WithdrawalRequestResponse(
                UUID.randomUUID(), new BigDecimal("300000"), "PENDING", "Yêu cầu rút tiền đã được gửi.", OffsetDateTime.now());
        when(service.createWithdrawal(currentUser, request)).thenReturn(expected);

        ResponseEntity<WithdrawalRequestResponse> response = controller.createWithdrawal(currentUser, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).createWithdrawal(currentUser, request);
    }

    @Test
    void getWithdrawalsUsesRequestedPageAndSizeWhenProvided() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        Page<DriverWithdrawalItemResponse> expected = new PageImpl<>(List.of(new DriverWithdrawalItemResponse(
                UUID.randomUUID(), new BigDecimal("300000"), "PENDING", "Vietcombank", "******6789",
                null, OffsetDateTime.now(), null)));
        when(service.getWithdrawals(driverId, 1, 5)).thenReturn(expected);

        Page<DriverWithdrawalItemResponse> actual = controller.getWithdrawals(currentUser, 1, 5);

        assertThat(actual).isEqualTo(expected);
        verify(service).getWithdrawals(driverId, 1, 5);
    }

    @Test
    void getWithdrawalsFallsBackToServiceDefaultPageSizeWhenSizeIsNull() {
        UUID driverId = UUID.randomUUID();
        User currentUser = User.builder().id(driverId).build();
        when(service.defaultPageSize()).thenReturn(20);
        Page<DriverWithdrawalItemResponse> expected = new PageImpl<>(List.of());
        when(service.getWithdrawals(driverId, 0, 20)).thenReturn(expected);

        Page<DriverWithdrawalItemResponse> actual = controller.getWithdrawals(currentUser, 0, null);

        assertThat(actual).isEqualTo(expected);
        verify(service).getWithdrawals(driverId, 0, 20);
    }
}
