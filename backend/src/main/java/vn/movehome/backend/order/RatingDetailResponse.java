package vn.movehome.backend.order;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Chi tiet danh gia cua mot don — FE dung de hien "Đã đánh giá ★x" va noi dung nhan xet. */
public record RatingDetailResponse(
        UUID ratingId,
        UUID orderId,
        Integer stars,
        String comment,
        OffsetDateTime createdAt
) {
}
