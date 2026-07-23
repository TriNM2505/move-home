package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import vn.movehome.backend.dto.admin.finance.PendingCustomerWithdrawalPageResponse;
import vn.movehome.backend.dto.admin.finance.ProcessWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.RejectWithdrawalRequest;
import vn.movehome.backend.dto.admin.finance.WithdrawalActionResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.service.AdminCustomerWithdrawalService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCustomerWithdrawalControllerTest {

    private final AdminCustomerWithdrawalService service = mock(AdminCustomerWithdrawalService.class);
    private final AdminCustomerWithdrawalController controller = new AdminCustomerWithdrawalController(service);

    @Test
    void getPendingUsesServiceDefaultSizeWhenSizeMissing() {
        PendingCustomerWithdrawalPageResponse expected = new PendingCustomerWithdrawalPageResponse(
                List.of(), 0, 20, 0, 0, true, true, 0, BigDecimal.ZERO, 0, 0);
        when(service.defaultPageSize()).thenReturn(20);
        when(service.getPending(0, 20)).thenReturn(expected);

        PendingCustomerWithdrawalPageResponse actual = controller.getPending(0, null);

        assertThat(actual).isEqualTo(expected);
        verify(service).getPending(0, 20);
    }

    @Test
    void getPendingUsesProvidedSize() {
        PendingCustomerWithdrawalPageResponse expected = new PendingCustomerWithdrawalPageResponse(
                List.of(), 1, 50, 0, 0, true, true, 0, BigDecimal.ZERO, 0, 0);
        when(service.getPending(1, 50)).thenReturn(expected);

        PendingCustomerWithdrawalPageResponse actual = controller.getPending(1, 50);

        assertThat(actual).isEqualTo(expected);
        verify(service).getPending(1, 50);
    }

    @Test
    void processDelegatesToService() {
        UUID withdrawalId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).role(UserRole.ADMIN).build();
        ProcessWithdrawalRequest request = new ProcessWithdrawalRequest("VCB-001", null);
        WithdrawalActionResponse expected = new WithdrawalActionResponse(
                withdrawalId, "PROCESSED", BigDecimal.TEN, BigDecimal.ONE, "Da ghi nhan chuyen khoan thanh cong");
        when(service.process(withdrawalId, admin, request)).thenReturn(expected);

        WithdrawalActionResponse actual = controller.process(withdrawalId, admin, "idem-key-1", request);

        assertThat(actual).isEqualTo(expected);
        verify(service).process(withdrawalId, admin, request);
    }

    @Test
    void rejectDelegatesToService() {
        UUID withdrawalId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).role(UserRole.ADMIN).build();
        RejectWithdrawalRequest request = new RejectWithdrawalRequest("Ly do tu choi hop le");
        WithdrawalActionResponse expected = new WithdrawalActionResponse(
                withdrawalId, "REJECTED", BigDecimal.TEN, null, "Da tu choi yeu cau rut tien");
        when(service.reject(withdrawalId, admin, request)).thenReturn(expected);

        WithdrawalActionResponse actual = controller.reject(withdrawalId, admin, null, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).reject(withdrawalId, admin, request);
    }
}
