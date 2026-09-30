package pl.jollycart.image;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferImageRepository
        extends JpaRepository<OfferImage, Long> {

    List<OfferImage> findByOfferIdOrderByCreatedAtAsc(Long offerId);
}