package vn.movehome.backend.order;

import java.util.UUID;

public record RatingResponse(
        UUID ratingId,
        UUID orderId,
        Integer stars,
        String message
) {
}
