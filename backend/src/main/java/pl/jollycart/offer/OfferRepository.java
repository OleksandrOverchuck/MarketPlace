package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findAllByOrderByCreatedAtDesc();

    List<Offer> findByStatusOrderByCreatedAtDesc(OfferStatus status);

    List<Offer> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Offer> findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCaseOrderByCreatedAtDesc(
            OfferStatus status1,
            String title,
            OfferStatus status2,
            String description
    );

    List<Offer> findByStatusAndCategoryIdOrderByCreatedAtDesc(
            OfferStatus status,
            Long categoryId
    );

    List<Offer> findByStatusAndTypeOrderByCreatedAtDesc(
            OfferStatus status,
            OfferType type
    );

    List<Offer> findByStatusAndPriceGreaterThanEqualOrderByCreatedAtDesc(
            OfferStatus status,
            BigDecimal minPrice
    );

    List<Offer> findByStatusAndPriceLessThanEqualOrderByCreatedAtDesc(
            OfferStatus status,
            BigDecimal maxPrice
    );

    List<Offer> findByStatusAndPriceBetweenOrderByCreatedAtDesc(
            OfferStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    );
}