package vn.movehome.backend.order;

public record RatingRequest(
        Integer stars,
        String comment
) {
}
