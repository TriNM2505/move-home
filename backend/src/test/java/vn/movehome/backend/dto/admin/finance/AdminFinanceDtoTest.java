package vn.movehome.backend.dto.admin.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem tra cac record DTO trong package dto.admin.finance: constructor, accessor,
 * equals/hashCode/toString (auto-sinh boi Java record) — dam bao coverage day du
 * cho cac lop du lieu thuan tuy nay.
 */
class AdminFinanceDtoTest {

    @Test
    void adminWithdrawalItemResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AdminWithdrawalItemResponse item = new AdminWithdrawalItemResponse(
                id, driverId, "Nguyen Van A", "0900000000", new BigDecimal("100000"),
                "VCB", "Vietcombank", "******7890", now, 2L, new BigDecimal("500000"),
                true, List.of());
        AdminWithdrawalItemResponse same = new AdminWithdrawalItemResponse(
                id, driverId, "Nguyen Van A", "0900000000", new BigDecimal("100000"),
                "VCB", "Vietcombank", "******7890", now, 2L, new BigDecimal("500000"),
                true, List.of());

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.driverId()).isEqualTo(driverId);
        assertThat(item.driverName()).isEqualTo("Nguyen Van A");
        assertThat(item.driverPhone()).isEqualTo("0900000000");
        assertThat(item.amount()).isEqualByComparingTo("100000");
        assertThat(item.bankCode()).isEqualTo("VCB");
        assertThat(item.bankName()).isEqualTo("Vietcombank");
        assertThat(item.bankAccountMasked()).isEqualTo("******7890");
        assertThat(item.requestedAt()).isEqualTo(now);
        assertThat(item.daysWaiting()).isEqualTo(2L);
        assertThat(item.walletBalance()).isEqualByComparingTo("500000");
        assertThat(item.processReady()).isTrue();
        assertThat(item.blockingReasons()).isEmpty();
        assertThat(item).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(item.toString()).contains("AdminWithdrawalItemResponse");
    }

    @Test
    void adminCustomerWithdrawalItemResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AdminCustomerWithdrawalItemResponse item = new AdminCustomerWithdrawalItemResponse(
                id, customerId, "Tran Thi B", "0911111111", new BigDecimal("200000"),
                "ACB", "ACB Bank", "******1234", now, 1L, new BigDecimal("900000"),
                false, List.of("BANK_ACCOUNT_MISSING"));

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.customerId()).isEqualTo(customerId);
        assertThat(item.customerName()).isEqualTo("Tran Thi B");
        assertThat(item.customerPhone()).isEqualTo("0911111111");
        assertThat(item.amount()).isEqualByComparingTo("200000");
        assertThat(item.bankCode()).isEqualTo("ACB");
        assertThat(item.bankName()).isEqualTo("ACB Bank");
        assertThat(item.bankAccountMasked()).isEqualTo("******1234");
        assertThat(item.requestedAt()).isEqualTo(now);
        assertThat(item.daysWaiting()).isEqualTo(1L);
        assertThat(item.walletBalance()).isEqualByComparingTo("900000");
        assertThat(item.processReady()).isFalse();
        assertThat(item.blockingReasons()).containsExactly("BANK_ACCOUNT_MISSING");
        assertThat(item).isEqualTo(item).hasSameHashCodeAs(item);
        assertThat(item.toString()).contains("AdminCustomerWithdrawalItemResponse");
    }

    @Test
    void adminTransactionResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AdminTransactionResponse response = new AdminTransactionResponse(
                id, "WITHDRAWAL", "Rut tien", new BigDecimal("-100000"), new BigDecimal("400000"),
                userId, "Nguyen Van A", "DRIVER", "n***@example.com", orderId, "MH-000001",
                withdrawalId, disputeId, "****7890", "****1234", "Rut tien ve tai khoan", now);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.type()).isEqualTo("WITHDRAWAL");
        assertThat(response.typeLabel()).isEqualTo("Rut tien");
        assertThat(response.amount()).isEqualByComparingTo("-100000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("400000");
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.userName()).isEqualTo("Nguyen Van A");
        assertThat(response.userRole()).isEqualTo("DRIVER");
        assertThat(response.userEmail()).isEqualTo("n***@example.com");
        assertThat(response.relatedOrderId()).isEqualTo(orderId);
        assertThat(response.orderCode()).isEqualTo("MH-000001");
        assertThat(response.relatedWithdrawalId()).isEqualTo(withdrawalId);
        assertThat(response.relatedDisputeId()).isEqualTo(disputeId);
        assertThat(response.vnpayTxnRefMasked()).isEqualTo("****7890");
        assertThat(response.bankTxnRefMasked()).isEqualTo("****1234");
        assertThat(response.description()).isEqualTo("Rut tien ve tai khoan");
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(response.toString()).contains("AdminTransactionResponse");
    }

    @Test
    void commissionSettingsResponseExposesAllFields() {
        UUID updatedBy = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        CommissionSettingsResponse response = new CommissionSettingsResponse(3L, new BigDecimal("0.3000"), now, updatedBy);

        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.commissionRate()).isEqualByComparingTo("0.3000");
        assertThat(response.lastUpdatedAt()).isEqualTo(now);
        assertThat(response.lastUpdatedBy()).isEqualTo(updatedBy);
        assertThat(response).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(response.toString()).contains("CommissionSettingsResponse");
    }

    @Test
    void pendingWithdrawalPageResponseExposesAllFields() {
        PendingWithdrawalPageResponse response = new PendingWithdrawalPageResponse(
                List.of(), 0, 20, 5L, 1, true, true, 5L, new BigDecimal("1000000"), 3L, 1L);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        assertThat(response.pendingCount()).isEqualTo(5L);
        assertThat(response.pendingAmount()).isEqualByComparingTo("1000000");
        assertThat(response.oldestWaitingDays()).isEqualTo(3L);
        assertThat(response.overSlaCount()).isEqualTo(1L);
        assertThat(response).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(response.toString()).contains("PendingWithdrawalPageResponse");
    }

    @Test
    void pendingCustomerWithdrawalPageResponseExposesAllFields() {
        PendingCustomerWithdrawalPageResponse response = new PendingCustomerWithdrawalPageResponse(
                List.of(), 0, 20, 5L, 1, true, true, 5L, new BigDecimal("1000000"), 3L, 1L);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        assertThat(response.pendingCount()).isEqualTo(5L);
        assertThat(response.pendingAmount()).isEqualByComparingTo("1000000");
        assertThat(response.oldestWaitingDays()).isEqualTo(3L);
        assertThat(response.overSlaCount()).isEqualTo(1L);
        assertThat(response).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(response.toString()).contains("PendingCustomerWithdrawalPageResponse");
    }

    @Test
    void processWithdrawalRequestExposesAllFields() {
        ProcessWithdrawalRequest request = new ProcessWithdrawalRequest("VCB-001", "Ghi chu");

        assertThat(request.bankTxnRef()).isEqualTo("VCB-001");
        assertThat(request.processingNote()).isEqualTo("Ghi chu");
        assertThat(request).isEqualTo(new ProcessWithdrawalRequest("VCB-001", "Ghi chu"));
        assertThat(request.toString()).contains("ProcessWithdrawalRequest");
    }

    @Test
    void rejectWithdrawalRequestExposesAllFields() {
        RejectWithdrawalRequest request = new RejectWithdrawalRequest("Ly do tu choi");

        assertThat(request.reason()).isEqualTo("Ly do tu choi");
        assertThat(request).isEqualTo(new RejectWithdrawalRequest("Ly do tu choi"));
        assertThat(request.toString()).contains("RejectWithdrawalRequest");
    }

    @Test
    void updateCommissionSettingsRequestExposesAllFields() {
        UpdateCommissionSettingsRequest request = new UpdateCommissionSettingsRequest(2L, new BigDecimal("0.2500"));

        assertThat(request.version()).isEqualTo(2L);
        assertThat(request.commissionRate()).isEqualByComparingTo("0.2500");
        assertThat(request).isEqualTo(new UpdateCommissionSettingsRequest(2L, new BigDecimal("0.2500")));
        assertThat(request.toString()).contains("UpdateCommissionSettingsRequest");
    }

    @Test
    void updateCommissionSettingsResponseExposesAllFields() {
        CommissionSettingsResponse newSettings = new CommissionSettingsResponse(
                4L, new BigDecimal("0.2500"), OffsetDateTime.now(), UUID.randomUUID());
        OffsetDateTime effectiveFrom = OffsetDateTime.now();
        UpdateCommissionSettingsResponse response = new UpdateCommissionSettingsResponse(
                "Da cap nhat cau hinh", newSettings, effectiveFrom, 4L);

        assertThat(response.message()).isEqualTo("Da cap nhat cau hinh");
        assertThat(response.newSettings()).isEqualTo(newSettings);
        assertThat(response.effectiveFrom()).isEqualTo(effectiveFrom);
        assertThat(response.version()).isEqualTo(4L);
        assertThat(response).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(response.toString()).contains("UpdateCommissionSettingsResponse");
    }

    @Test
    void withdrawalActionResponseExposesAllFields() {
        UUID withdrawalId = UUID.randomUUID();
        WithdrawalActionResponse response = new WithdrawalActionResponse(
                withdrawalId, "PROCESSED", new BigDecimal("100000"), new BigDecimal("400000"), "Thanh cong");

        assertThat(response.withdrawalId()).isEqualTo(withdrawalId);
        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(response.amount()).isEqualByComparingTo("100000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("400000");
        assertThat(response.message()).isEqualTo("Thanh cong");
        assertThat(response).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(response.toString()).contains("WithdrawalActionResponse");
    }
}
