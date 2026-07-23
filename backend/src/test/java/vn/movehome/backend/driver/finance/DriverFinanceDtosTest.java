package vn.movehome.backend.driver.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cac DTO thuan tuy (record) trong package driver.finance — khong chua logic, chi kiem tra
 * constructor + accessor + equals/hashCode/toString sinh ra boi compiler de dat coverage.
 */
class DriverFinanceDtosTest {

    @Test
    void driverEarningResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        DriverEarningResponse response = new DriverEarningResponse(
                id, new BigDecimal("700000"), orderId, "MH202606200001", "Thu nhập tài xế", createdAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.amount()).isEqualByComparingTo("700000");
        assertThat(response.relatedOrderId()).isEqualTo(orderId);
        assertThat(response.orderCode()).isEqualTo("MH202606200001");
        assertThat(response.description()).isEqualTo("Thu nhập tài xế");
        assertThat(response.createdAt()).isEqualTo(createdAt);

        DriverEarningResponse same = new DriverEarningResponse(
                id, new BigDecimal("700000"), orderId, "MH202606200001", "Thu nhập tài xế", createdAt);
        assertThat(response).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(response.toString()).contains("DriverEarningResponse");
    }

    @Test
    void driverWalletSummaryResponseExposesAllFields() {
        DriverWalletSummaryResponse response = new DriverWalletSummaryResponse(
                new BigDecimal("100000"), new BigDecimal("300000"), new BigDecimal("200000"));

        assertThat(response.balance()).isEqualByComparingTo("100000");
        assertThat(response.totalEarned()).isEqualByComparingTo("300000");
        assertThat(response.totalWithdrawn()).isEqualByComparingTo("200000");

        DriverWalletSummaryResponse same = new DriverWalletSummaryResponse(
                new BigDecimal("100000"), new BigDecimal("300000"), new BigDecimal("200000"));
        assertThat(response).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(response.toString()).contains("DriverWalletSummaryResponse");
    }

    @Test
    void driverWithdrawalItemResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        OffsetDateTime processedAt = requestedAt.plusHours(1);
        DriverWithdrawalItemResponse response = new DriverWithdrawalItemResponse(
                id, new BigDecimal("300000"), "PROCESSED", "Vietcombank", "******6789",
                null, requestedAt, processedAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.amount()).isEqualByComparingTo("300000");
        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.bankAccountMasked()).isEqualTo("******6789");
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.requestedAt()).isEqualTo(requestedAt);
        assertThat(response.processedAt()).isEqualTo(processedAt);
        assertThat(response.toString()).contains("DriverWithdrawalItemResponse");
    }

    @Test
    void withdrawalRequestResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        WithdrawalRequestResponse response = new WithdrawalRequestResponse(
                id, new BigDecimal("300000"), "PENDING", "Yêu cầu rút tiền đã được gửi.", requestedAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.amount()).isEqualByComparingTo("300000");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).isEqualTo("Yêu cầu rút tiền đã được gửi.");
        assertThat(response.requestedAt()).isEqualTo(requestedAt);
        assertThat(response.toString()).contains("WithdrawalRequestResponse");
    }

    @Test
    void createWithdrawalRequestExposesAllFields() {
        CreateWithdrawalRequest request = new CreateWithdrawalRequest(new BigDecimal("300000"), "VCB", "0123456789");

        assertThat(request.amount()).isEqualByComparingTo("300000");
        assertThat(request.bankCode()).isEqualTo("VCB");
        assertThat(request.bankAccountNumber()).isEqualTo("0123456789");
        assertThat(request.toString()).contains("CreateWithdrawalRequest");
    }
}
