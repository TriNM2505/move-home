package vn.movehome.backend.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO/record thuan du lieu — kiem tra constructor + accessor de dam bao khop
 * data model, khong co logic nghiep vu ben trong (order cluster).
 */
class OrderCancellationDtosTest {

    @Test
    void createOrderRequestExposesAllAccessors() {
        CreateOrderRequest.Location pickup = new CreateOrderRequest.Location(
                "123 Duong Lang, Dong Da, Ha Noi", "DONG_DA",
                new BigDecimal("21.0123"), new BigDecimal("105.8123"),
                3, true, false);
        CreateOrderRequest.Location dropoff = new CreateOrderRequest.Location(
                "456 Cau Giay, Ha Noi", "CAU_GIAY",
                new BigDecimal("21.0300"), new BigDecimal("105.7900"),
                1, false, true);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(2);

        CreateOrderRequest request = new CreateOrderRequest(
                "TRUCK_500KG", pickup, dropoff, scheduledAt, 2, "Can boc xep can than");

        assertThat(request.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(request.pickup()).isEqualTo(pickup);
        assertThat(request.dropoff()).isEqualTo(dropoff);
        assertThat(request.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(request.porterCount()).isEqualTo(2);
        assertThat(request.notes()).isEqualTo("Can boc xep can than");

        assertThat(pickup.address()).isEqualTo("123 Duong Lang, Dong Da, Ha Noi");
        assertThat(pickup.district()).isEqualTo("DONG_DA");
        assertThat(pickup.lat()).isEqualByComparingTo("21.0123");
        assertThat(pickup.lng()).isEqualByComparingTo("105.8123");
        assertThat(pickup.floor()).isEqualTo(3);
        assertThat(pickup.hasElevator()).isTrue();
        assertThat(pickup.hasAlley()).isFalse();

        assertThat(dropoff.address()).isEqualTo("456 Cau Giay, Ha Noi");
        assertThat(dropoff.district()).isEqualTo("CAU_GIAY");
        assertThat(dropoff.lat()).isEqualByComparingTo("21.0300");
        assertThat(dropoff.lng()).isEqualByComparingTo("105.7900");
        assertThat(dropoff.floor()).isEqualTo(1);
        assertThat(dropoff.hasElevator()).isFalse();
        assertThat(dropoff.hasAlley()).isTrue();
    }

    @Test
    void createOrderResponseExposesAllAccessors() {
        UUID id = UUID.randomUUID();
        CreateOrderResponse response = new CreateOrderResponse(id, "MH0001", "PENDING_PAYMENT",
                new BigDecimal("1000000"));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.orderCode()).isEqualTo("MH0001");
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.totalQuote()).isEqualByComparingTo("1000000");
    }

    @Test
    void ratingRequestExposesAllAccessors() {
        RatingRequest request = new RatingRequest(5, "Tai xe rat nhiet tinh");

        assertThat(request.stars()).isEqualTo(5);
        assertThat(request.comment()).isEqualTo("Tai xe rat nhiet tinh");
    }

    @Test
    void ratingResponseExposesAllAccessors() {
        UUID ratingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RatingResponse response = new RatingResponse(ratingId, orderId, 4, "Danh gia thanh cong");

        assertThat(response.ratingId()).isEqualTo(ratingId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.stars()).isEqualTo(4);
        assertThat(response.message()).isEqualTo("Danh gia thanh cong");
    }

    @Test
    void ratingDetailResponseExposesAllAccessors() {
        UUID ratingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        RatingDetailResponse response = new RatingDetailResponse(ratingId, orderId, 3, "Binh thuong", createdAt);

        assertThat(response.ratingId()).isEqualTo(ratingId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.stars()).isEqualTo(3);
        assertThat(response.comment()).isEqualTo("Binh thuong");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void cancellationRefundListItemExposesAllAccessors() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime processedAt = OffsetDateTime.now().plusMinutes(5);

        CancellationRefundListItem item = new CancellationRefundListItem(
                id, orderId, "MH0002", customerId, "Nguyen Van A", "Doi y khong chuyen nua",
                OrderCancellationRefund.STATUS_PENDING, new BigDecimal("300000"), null, createdAt, processedAt);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.orderId()).isEqualTo(orderId);
        assertThat(item.orderCode()).isEqualTo("MH0002");
        assertThat(item.customerId()).isEqualTo(customerId);
        assertThat(item.customerName()).isEqualTo("Nguyen Van A");
        assertThat(item.reason()).isEqualTo("Doi y khong chuyen nua");
        assertThat(item.status()).isEqualTo(OrderCancellationRefund.STATUS_PENDING);
        assertThat(item.depositAmount()).isEqualByComparingTo("300000");
        assertThat(item.refundAmount()).isNull();
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(item.processedAt()).isEqualTo(processedAt);
    }

    @Test
    void cancellationRefundDetailResponseExposesAllAccessorsIncludingOrderSummary() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID processedBy = UUID.randomUUID();
        OffsetDateTime processedAt = OffsetDateTime.now();
        OffsetDateTime createdAt = OffsetDateTime.now().minusHours(1);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        List<String> photoUrls = List.of("https://example.com/1.jpg");
        CancellationRefundDetailResponse.OrderSummary summary = new CancellationRefundDetailResponse.OrderSummary(
                "123 Duong Lang", "456 Cau Giay", new BigDecimal("2000000"), scheduledAt);

        CancellationRefundDetailResponse response = new CancellationRefundDetailResponse(
                id, orderId, "MH0003", "CANCELLED", customerId, "Tran Thi B", "+84900000000",
                "Doi y", OrderCancellationRefund.STATUS_REFUNDED, new BigDecimal("600000"),
                new BigDecimal("600000"), null, processedBy, processedAt, createdAt, photoUrls, summary);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderCode()).isEqualTo("MH0003");
        assertThat(response.orderStatus()).isEqualTo("CANCELLED");
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.customerName()).isEqualTo("Tran Thi B");
        assertThat(response.customerPhone()).isEqualTo("+84900000000");
        assertThat(response.reason()).isEqualTo("Doi y");
        assertThat(response.status()).isEqualTo(OrderCancellationRefund.STATUS_REFUNDED);
        assertThat(response.depositAmount()).isEqualByComparingTo("600000");
        assertThat(response.refundAmount()).isEqualByComparingTo("600000");
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.processedBy()).isEqualTo(processedBy);
        assertThat(response.processedAt()).isEqualTo(processedAt);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.photoUrls()).isEqualTo(photoUrls);
        assertThat(response.order()).isEqualTo(summary);

        assertThat(summary.pickupAddress()).isEqualTo("123 Duong Lang");
        assertThat(summary.dropoffAddress()).isEqualTo("456 Cau Giay");
        assertThat(summary.totalQuote()).isEqualByComparingTo("2000000");
        assertThat(summary.scheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    void customerOrderDetailResponseExposesAllAccessors() {
        UUID id = UUID.randomUUID();
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(1);
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        OffsetDateTime completedAt = OffsetDateTime.now();
        OffsetDateTime cancelledAt = null;

        CustomerOrderDetailResponse response = new CustomerOrderDetailResponse(
                id, "MH0004", "COMPLETED", "TRUCK_1T",
                "123 Duong Lang", "DONG_DA", new BigDecimal("21.01"), new BigDecimal("105.81"),
                "456 Cau Giay", "CAU_GIAY", new BigDecimal("21.03"), new BigDecimal("105.79"),
                scheduledAt, new BigDecimal("12.5"),
                new BigDecimal("500000"), new BigDecimal("50000"), new BigDecimal("30000"),
                new BigDecimal("20000"), new BigDecimal("100000"), 2,
                new BigDecimal("700000"),
                new BigDecimal("210000"), true, new BigDecimal("490000"), true,
                "Nguyen Van Tai", "Ghi chu don hang", createdAt, completedAt, cancelledAt);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.orderCode()).isEqualTo("MH0004");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.vehicleType()).isEqualTo("TRUCK_1T");
        assertThat(response.pickupAddress()).isEqualTo("123 Duong Lang");
        assertThat(response.pickupDistrict()).isEqualTo("DONG_DA");
        assertThat(response.pickupLat()).isEqualByComparingTo("21.01");
        assertThat(response.pickupLng()).isEqualByComparingTo("105.81");
        assertThat(response.dropoffAddress()).isEqualTo("456 Cau Giay");
        assertThat(response.dropoffDistrict()).isEqualTo("CAU_GIAY");
        assertThat(response.dropoffLat()).isEqualByComparingTo("21.03");
        assertThat(response.dropoffLng()).isEqualByComparingTo("105.79");
        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(response.distanceKm()).isEqualByComparingTo("12.5");
        assertThat(response.baseFare()).isEqualByComparingTo("500000");
        assertThat(response.peakSurcharge()).isEqualByComparingTo("50000");
        assertThat(response.alleySurcharge()).isEqualByComparingTo("30000");
        assertThat(response.floorSurcharge()).isEqualByComparingTo("20000");
        assertThat(response.porterFee()).isEqualByComparingTo("100000");
        assertThat(response.porterCount()).isEqualTo(2);
        assertThat(response.totalQuote()).isEqualByComparingTo("700000");
        assertThat(response.depositAmount()).isEqualByComparingTo("210000");
        assertThat(response.depositPaid()).isTrue();
        assertThat(response.remainingAmount()).isEqualByComparingTo("490000");
        assertThat(response.finalPaid()).isTrue();
        assertThat(response.driverName()).isEqualTo("Nguyen Van Tai");
        assertThat(response.notes()).isEqualTo("Ghi chu don hang");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.completedAt()).isEqualTo(completedAt);
        assertThat(response.cancelledAt()).isNull();
    }

    @Test
    void customerDriverVerificationResponseExposesAllAccessors() {
        CustomerDriverVerificationResponse response = new CustomerDriverVerificationResponse(
                "Nguyen Van Tai", "+84911111111", "TRUCK_500KG", "30A-123.45",
                "https://example.com/face.jpg", "https://example.com/vehicle.jpg", true);

        assertThat(response.driverName()).isEqualTo("Nguyen Van Tai");
        assertThat(response.driverPhone()).isEqualTo("+84911111111");
        assertThat(response.vehicleType()).isEqualTo("TRUCK_500KG");
        assertThat(response.vehiclePlate()).isEqualTo("30A-123.45");
        assertThat(response.facePhotoUrl()).isEqualTo("https://example.com/face.jpg");
        assertThat(response.vehiclePhotoUrl()).isEqualTo("https://example.com/vehicle.jpg");
        assertThat(response.cancellable()).isTrue();
    }

    @Test
    void rejectCancellationRequestExposesReason() {
        RejectCancellationRequest request = new RejectCancellationRequest("Khong du dieu kien hoan coc");

        assertThat(request.reason()).isEqualTo("Khong du dieu kien hoan coc");
    }
}
