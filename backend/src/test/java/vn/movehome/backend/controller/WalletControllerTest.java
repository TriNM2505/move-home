package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.movehome.backend.customer.finance.CreateCustomerWithdrawalRequest;
import vn.movehome.backend.customer.finance.CustomerWithdrawalItemResponse;
import vn.movehome.backend.customer.finance.CustomerWithdrawalRequestResponse;
import vn.movehome.backend.dto.customer.wallet.TransactionDTO;
import vn.movehome.backend.dto.customer.wallet.WalletSummaryDTO;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.WalletService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletControllerTest {

    private final WalletService service = mock(WalletService.class);
    private final WalletController controller = new WalletController(service);

    @Test
    void getWalletUsesAuthenticatedPrincipalId() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        WalletSummaryDTO expected = new WalletSummaryDTO(
                new BigDecimal("100000"), new BigDecimal("200000"), new BigDecimal("50000"), new BigDecimal("0"));
        when(service.getOrCreateSummary(customerId)).thenReturn(expected);

        WalletSummaryDTO actual = controller.getWallet(currentUser);

        assertThat(actual).isEqualTo(expected);
        verify(service).getOrCreateSummary(customerId);
    }

    @Test
    void getTransactionsUsesDefaultPageSizeWhenSizeMissing() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        Page<TransactionDTO> expected = new PageImpl<>(List.of());
        when(service.defaultPageSize()).thenReturn(20);
        when(service.getTransactions(customerId, 0, 20)).thenReturn(expected);

        Page<TransactionDTO> actual = controller.getTransactions(currentUser, 0, null);

        assertThat(actual).isEqualTo(expected);
        verify(service).getTransactions(customerId, 0, 20);
    }

    @Test
    void getTransactionsUsesProvidedSize() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        Page<TransactionDTO> expected = new PageImpl<>(List.of());
        when(service.getTransactions(customerId, 2, 5)).thenReturn(expected);

        Page<TransactionDTO> actual = controller.getTransactions(currentUser, 2, 5);

        assertThat(actual).isEqualTo(expected);
        verify(service).getTransactions(customerId, 2, 5);
        verify(service, org.mockito.Mockito.never()).defaultPageSize();
    }

    @Test
    void createWithdrawalReturnsCreatedStatusWithBody() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        CreateCustomerWithdrawalRequest request =
                new CreateCustomerWithdrawalRequest(new BigDecimal("100000"), "VCB", "12345678");
        CustomerWithdrawalRequestResponse expected = new CustomerWithdrawalRequestResponse(
                UUID.randomUUID(), new BigDecimal("100000"), "PENDING",
                "Yêu cầu rút tiền đã được gửi.", OffsetDateTime.now());
        when(service.createWithdrawal(currentUser, request)).thenReturn(expected);

        ResponseEntity<CustomerWithdrawalRequestResponse> response = controller.createWithdrawal(currentUser, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).createWithdrawal(currentUser, request);
    }

    @Test
    void getWithdrawalsUsesDefaultPageSizeWhenSizeMissing() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        Page<CustomerWithdrawalItemResponse> expected = new PageImpl<>(List.of());
        when(service.defaultPageSize()).thenReturn(20);
        when(service.getWithdrawals(customerId, 0, 20)).thenReturn(expected);

        Page<CustomerWithdrawalItemResponse> actual = controller.getWithdrawals(currentUser, 0, null);

        assertThat(actual).isEqualTo(expected);
        verify(service).getWithdrawals(customerId, 0, 20);
    }

    @Test
    void getWithdrawalsUsesProvidedSize() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        Page<CustomerWithdrawalItemResponse> expected = new PageImpl<>(List.of());
        when(service.getWithdrawals(customerId, 1, 15)).thenReturn(expected);

        Page<CustomerWithdrawalItemResponse> actual = controller.getWithdrawals(currentUser, 1, 15);

        assertThat(actual).isEqualTo(expected);
        verify(service).getWithdrawals(customerId, 1, 15);
    }
}
