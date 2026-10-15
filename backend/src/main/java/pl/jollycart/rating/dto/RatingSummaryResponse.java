package pl.jollycart.rating.dto;

public record RatingSummaryResponse(
        double averageRating,
        long ratingCount,
        Integer myRating
) {
}
