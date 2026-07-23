package vn.movehome.backend.customer.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cac DTO thuan tuy (record) trong package customer.finance — khong chua logic, chi kiem tra
 * constructor + accessor + equals/hashCode/toString sinh ra boi compiler de dat coverage.
 */
class CustomerFinanceDtosTest {

    @Test
    void createCustomerWithdrawalRequestExposesAllFields() {
        CreateCustomerWithdrawalRequest request = new CreateCustomerWithdrawalRequest(
                new BigDecimal("500000"), "BIDV", "0987654321");

        assertThat(request.amount()).isEqualByComparingTo("500000");
        assertThat(request.bankCode()).isEqualTo("BIDV");
        assertThat(request.bankAccountNumber()).isEqualTo("0987654321");

        CreateCustomerWithdrawalRequest same = new CreateCustomerWithdrawalRequest(
                new BigDecimal("500000"), "BIDV", "0987654321");
        assertThat(request).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(request.toString()).contains("CreateCustomerWithdrawalRequest");
    }

    @Test
    void customerWithdrawalItemResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        OffsetDateTime processedAt = requestedAt.plusHours(2);
        CustomerWithdrawalItemResponse response = new CustomerWithdrawalItemResponse(
                id, new BigDecimal("500000"), "REJECTED", "BIDV", "******4321",
                "Sai thông tin", requestedAt, processedAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.amount()).isEqualByComparingTo("500000");
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.bankName()).isEqualTo("BIDV");
        assertThat(response.bankAccountMasked()).isEqualTo("******4321");
        assertThat(response.rejectionReason()).isEqualTo("Sai thông tin");
        assertThat(response.requestedAt()).isEqualTo(requestedAt);
        assertThat(response.processedAt()).isEqualTo(processedAt);

        CustomerWithdrawalItemResponse same = new CustomerWithdrawalItemResponse(
                id, new BigDecimal("500000"), "REJECTED", "BIDV", "******4321",
                "Sai thông tin", requestedAt, processedAt);
        assertThat(response).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(response.toString()).contains("CustomerWithdrawalItemResponse");
    }

    @Test
    void customerWithdrawalRequestResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        CustomerWithdrawalRequestResponse response = new CustomerWithdrawalRequestResponse(
                id, new BigDecimal("500000"), "PENDING", "Yêu cầu rút tiền đã được gửi.", requestedAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.amount()).isEqualByComparingTo("500000");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).isEqualTo("Yêu cầu rút tiền đã được gửi.");
        assertThat(response.requestedAt()).isEqualTo(requestedAt);
        assertThat(response.toString()).contains("CustomerWithdrawalRequestResponse");
    }
}
