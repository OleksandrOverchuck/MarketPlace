package pl.jollycart.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.OfferType;

public record StaffOfferResponse(
        Long id,
        String title,
        OfferStatus status,
        OfferType type,
        BigDecimal price,
        String location,
        Long userId,
        String userNickname,
        LocalDateTime createdAt
) {

    public static StaffOfferResponse from(Offer offer) {
        return new StaffOfferResponse(
                offer.getId(),
                offer.getTitle(),
                offer.getStatus(),
                offer.getType(),
                offer.getPrice(),
                offer.getLocation(),
                offer.getUser().getId(),
                offer.getUser().getNickname(),
                offer.getCreatedAt()
        );
    }
}
