package pl.jollycart.offer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.OfferType;

public record OfferResponse(

        Long id,

        String title,

        String description,

        BigDecimal price,

        OfferType type,

        OfferStatus status,

        Long userId,

        String nickname,

        Long categoryId,

        String categoryName,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {

    public static OfferResponse from(Offer offer) {

        return new OfferResponse(
                offer.getId(),
                offer.getTitle(),
                offer.getDescription(),
                offer.getPrice(),
                offer.getType(),
                offer.getStatus(),
                offer.getUser().getId(),
                offer.getUser().getNickname(),
                offer.getCategory().getId(),
                offer.getCategory().getName(),
                offer.getCreatedAt(),
                offer.getUpdatedAt()
        );
    }
}