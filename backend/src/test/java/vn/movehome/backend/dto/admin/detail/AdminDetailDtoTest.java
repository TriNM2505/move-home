package vn.movehome.backend.dto.admin.detail;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO thuan tuy (record) — chi kiem tra constructor + accessor giu dung gia tri.
 * Muc tieu: dat instruction coverage cho cac nested record it/khong duoc cac Service test
 * xay dung truc tiep (vi du: cac muc "rong" trong response nhu dispute/login history).
 */
class AdminDetailDtoTest {

    @Test
    void adminOrderDetailResponseAndNestedSectionsExposeConstructorValues() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        AdminOrderDetailResponse.OrderSection order = new AdminOrderDetailResponse.OrderSection(
                orderId, "MH-1", "COMPLETED", "TRUCK_500KG", 1,
                new BigDecimal("5.00"), now, now, "note", "reason");
        AdminOrderDetailResponse.PartySection party = new AdminOrderDetailResponse.PartySection(
                userId, "Nguyen Van A", "****123");
        AdminOrderDetailResponse.LocationSection location = new AdminOrderDetailResponse.LocationSection(
                "123 Le Loi", "District 1");
        AdminOrderDetailResponse.PricingSection pricing = new AdminOrderDetailResponse.PricingSection(
                new BigDecimal("500000"), new BigDecimal("10000"), new BigDecimal("20000"),
                new BigDecimal("30000"), new BigDecimal("40000"), new BigDecimal("600000"),
                new BigDecimal("0.3000"));
        AdminOrderDetailResponse.TimelineItem timelineItem =
                new AdminOrderDetailResponse.TimelineItem("Tạo đơn", now);
        AdminOrderDetailResponse.TransactionItem transactionItem = new AdminOrderDetailResponse.TransactionItem(
                "ORDER_PAYMENT", "Thanh toán đơn", "Nguyen Van A", new BigDecimal("600000"));

        AdminOrderDetailResponse response = new AdminOrderDetailResponse(
                order, party, party, location, location, pricing,
                List.of(timelineItem), List.of(transactionItem));

        assertThat(response.order().id()).isEqualTo(orderId);
        assertThat(response.order().orderCode()).isEqualTo("MH-1");
        assertThat(response.order().status()).isEqualTo("COMPLETED");
        assertThat(response.order().vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(response.order().porterCount()).isEqualTo(1);
        assertThat(response.order().distanceKm()).isEqualByComparingTo("5.00");
        assertThat(response.order().scheduledAt()).isEqualTo(now);
        assertThat(response.order().createdAt()).isEqualTo(now);
        assertThat(response.order().notes()).isEqualTo("note");
        assertThat(response.order().cancellationReason()).isEqualTo("reason");
        assertThat(response.customer().id()).isEqualTo(userId);
        assertThat(response.customer().fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.customer().phoneMasked()).isEqualTo("****123");
        assertThat(response.driver()).isEqualTo(party);
        assertThat(response.pickup().address()).isEqualTo("123 Le Loi");
        assertThat(response.pickup().district()).isEqualTo("District 1");
        assertThat(response.dropoff()).isEqualTo(location);
        assertThat(response.pricing().baseFare()).isEqualByComparingTo("500000");
        assertThat(response.pricing().peakSurcharge()).isEqualByComparingTo("10000");
        assertThat(response.pricing().alleySurcharge()).isEqualByComparingTo("20000");
        assertThat(response.pricing().floorSurcharge()).isEqualByComparingTo("30000");
        assertThat(response.pricing().porterFee()).isEqualByComparingTo("40000");
        assertThat(response.pricing().totalQuote()).isEqualByComparingTo("600000");
        assertThat(response.pricing().commissionRateSnapshot()).isEqualByComparingTo("0.3000");
        assertThat(response.timeline()).containsExactly(timelineItem);
        assertThat(timelineItem.label()).isEqualTo("Tạo đơn");
        assertThat(timelineItem.at()).isEqualTo(now);
        assertThat(response.transactions()).containsExactly(transactionItem);
        assertThat(transactionItem.type()).isEqualTo("ORDER_PAYMENT");
        assertThat(transactionItem.typeLabel()).isEqualTo("Thanh toán đơn");
        assertThat(transactionItem.userName()).isEqualTo("Nguyen Van A");
        assertThat(transactionItem.amount()).isEqualByComparingTo("600000");
    }

    @Test
    void auditLogItemExposesConstructorValues() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        AuditLogItem item = new AuditLogItem(
                id, "USER_SUSPENDED", actorId, "admin@movehome.vn", "USER", "entity-1", "detail", now);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.eventType()).isEqualTo("USER_SUSPENDED");
        assertThat(item.actorId()).isEqualTo(actorId);
        assertThat(item.actorEmail()).isEqualTo("admin@movehome.vn");
        assertThat(item.entityType()).isEqualTo("USER");
        assertThat(item.entityId()).isEqualTo("entity-1");
        assertThat(item.detail()).isEqualTo("detail");
        assertThat(item.createdAt()).isEqualTo(now);
    }

    @Test
    void driverOrderItemAndCustomerOrderItemExposeConstructorValues() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        DriverOrderItem driverOrderItem = new DriverOrderItem(
                id, "MH-2", "ACCEPTED", "Nguyen Van A", "District 1", "District 2",
                "TRUCK_500KG", new BigDecimal("700000"), now, now);
        CustomerOrderItem customerOrderItem = new CustomerOrderItem(
                id, "MH-3", "COMPLETED", "Tran Van B", "District 3", "District 4",
                "TRUCK_1000KG", new BigDecimal("800000"), now, now);

        assertThat(driverOrderItem.id()).isEqualTo(id);
        assertThat(driverOrderItem.orderCode()).isEqualTo("MH-2");
        assertThat(driverOrderItem.status()).isEqualTo("ACCEPTED");
        assertThat(driverOrderItem.customerName()).isEqualTo("Nguyen Van A");
        assertThat(driverOrderItem.pickupDistrict()).isEqualTo("District 1");
        assertThat(driverOrderItem.dropoffDistrict()).isEqualTo("District 2");
        assertThat(driverOrderItem.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(driverOrderItem.totalQuote()).isEqualByComparingTo("700000");
        assertThat(driverOrderItem.createdAt()).isEqualTo(now);
        assertThat(driverOrderItem.scheduledAt()).isEqualTo(now);

        assertThat(customerOrderItem.id()).isEqualTo(id);
        assertThat(customerOrderItem.orderCode()).isEqualTo("MH-3");
        assertThat(customerOrderItem.status()).isEqualTo("COMPLETED");
        assertThat(customerOrderItem.driverName()).isEqualTo("Tran Van B");
        assertThat(customerOrderItem.pickupDistrict()).isEqualTo("District 3");
        assertThat(customerOrderItem.dropoffDistrict()).isEqualTo("District 4");
        assertThat(customerOrderItem.vehicleType()).isEqualTo("TRUCK_1000KG");
        assertThat(customerOrderItem.totalQuote()).isEqualByComparingTo("800000");
        assertThat(customerOrderItem.createdAt()).isEqualTo(now);
        assertThat(customerOrderItem.scheduledAt()).isEqualTo(now);
    }

    @Test
    void driverDetailResponseNestedRecentOrderAndWithdrawalItemsExposeConstructorValues() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        DriverDetailResponse.RecentOrderItem recentOrder = new DriverDetailResponse.RecentOrderItem(
                id, "MH-4", "COMPLETED", "District 1", "District 2",
                new BigDecimal("900000"), now);
        DriverDetailResponse.RecentWithdrawalItem recentWithdrawal = new DriverDetailResponse.RecentWithdrawalItem(
                id, new BigDecimal("500000"), "PROCESSED", now, now);
        DriverDetailResponse.LastKnownLocation location = new DriverDetailResponse.LastKnownLocation(
                new BigDecimal("10.1234567"), new BigDecimal("106.1234567"), now);

        assertThat(recentOrder.id()).isEqualTo(id);
        assertThat(recentOrder.orderCode()).isEqualTo("MH-4");
        assertThat(recentOrder.status()).isEqualTo("COMPLETED");
        assertThat(recentOrder.pickupDistrict()).isEqualTo("District 1");
        assertThat(recentOrder.dropoffDistrict()).isEqualTo("District 2");
        assertThat(recentOrder.totalQuote()).isEqualByComparingTo("900000");
        assertThat(recentOrder.createdAt()).isEqualTo(now);

        assertThat(recentWithdrawal.id()).isEqualTo(id);
        assertThat(recentWithdrawal.amount()).isEqualByComparingTo("500000");
        assertThat(recentWithdrawal.status()).isEqualTo("PROCESSED");
        assertThat(recentWithdrawal.requestedAt()).isEqualTo(now);
        assertThat(recentWithdrawal.processedAt()).isEqualTo(now);

        assertThat(location.lat()).isEqualByComparingTo("10.1234567");
        assertThat(location.lng()).isEqualByComparingTo("106.1234567");
        assertThat(location.updatedAt()).isEqualTo(now);
    }

    @Test
    void customerDetailResponseNestedDisputeAndLoginHistoryItemsExposeConstructorValues() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        CustomerDetailResponse.RecentOrderItem recentOrder = new CustomerDetailResponse.RecentOrderItem(
                id, "MH-5", "CANCELLED", "District 5", "District 6",
                new BigDecimal("400000"), now);
        CustomerDetailResponse.DisputePreviewItem disputePreview = new CustomerDetailResponse.DisputePreviewItem(
                id, "MH-6", "IN_DISPUTE", now);
        CustomerDetailResponse.LoginHistoryItem loginHistory = new CustomerDetailResponse.LoginHistoryItem(
                now, "123.***.***.45", "Chrome trên Windows");

        assertThat(recentOrder.id()).isEqualTo(id);
        assertThat(recentOrder.orderCode()).isEqualTo("MH-5");
        assertThat(recentOrder.status()).isEqualTo("CANCELLED");
        assertThat(recentOrder.pickupDistrict()).isEqualTo("District 5");
        assertThat(recentOrder.dropoffDistrict()).isEqualTo("District 6");
        assertThat(recentOrder.totalQuote()).isEqualByComparingTo("400000");
        assertThat(recentOrder.createdAt()).isEqualTo(now);

        assertThat(disputePreview.id()).isEqualTo(id);
        assertThat(disputePreview.orderCode()).isEqualTo("MH-6");
        assertThat(disputePreview.status()).isEqualTo("IN_DISPUTE");
        assertThat(disputePreview.createdAt()).isEqualTo(now);

        assertThat(loginHistory.loggedInAt()).isEqualTo(now);
        assertThat(loginHistory.ipMasked()).isEqualTo("123.***.***.45");
        assertThat(loginHistory.userAgentSummary()).isEqualTo("Chrome trên Windows");
    }
}
