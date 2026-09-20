package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.category.Category;
import pl.jollycart.category.CategoryRepository;
import pl.jollycart.offer.dto.CreateOfferRequest;
import pl.jollycart.offer.dto.OfferResponse;
import pl.jollycart.offer.dto.UpdateOfferRequest;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class OfferService {

    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public OfferService(
            OfferRepository offerRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository
    ) {
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    public OfferResponse createOffer(
            String currentEmail,
            CreateOfferRequest request
    ) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        Category category = categoryRepository.findById(
                        request.categoryId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Kategoria nie została znaleziona"
                        )
                );

        Offer offer = new Offer();

        offer.setTitle(request.title());
        offer.setDescription(request.description());
        offer.setPrice(request.price());
        offer.setType(request.type());
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setUser(user);
        offer.setCategory(category);

        Offer savedOffer = offerRepository.save(offer);

        return OfferResponse.from(savedOffer);
    }

    @Transactional(readOnly = true)
    public OfferResponse getOfferById(Long id) {

        Offer offer = offerRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Oferta nie została znaleziona"
                        )
                );

        return OfferResponse.from(offer);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getAllActiveOffers() {

        return offerRepository
                .findByStatusOrderByCreatedAtDesc(
                        OfferStatus.ACTIVE
                )
                .stream()
                .map(OfferResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getOffersByUser(
            Long userId
    ) {

        return offerRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OfferResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> searchOffers(
            String search,
            Long categoryId,
            OfferType type,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {

        List<Offer> offers;

        if (search != null && !search.isBlank()) {

            offers = offerRepository
                    .findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCaseOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE,
                            search,
                            OfferStatus.ACTIVE,
                            search
                    );

        } else if (categoryId != null) {

            offers = offerRepository
                    .findByStatusAndCategoryIdOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE,
                            categoryId
                    );

        } else if (type != null) {

            offers = offerRepository
                    .findByStatusAndTypeOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE,
                            type
                    );

        } else if (minPrice != null && maxPrice != null) {

            offers = offerRepository
                    .findByStatusAndPriceBetweenOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE,
                            minPrice,
                            maxPrice
                    );

        } else if (minPrice != null) {

            offers = offerRepository
                    .findByStatusAndPriceGreaterThanEqualOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE,
                            minPrice
                    );

        } else if (maxPrice != null) {

            offers = offerRepository
                    .findByStatusAndPriceLessThanEqualOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE,
                            maxPrice
                    );

        } else {

            offers = offerRepository
                    .findByStatusOrderByCreatedAtDesc(
                            OfferStatus.ACTIVE
                    );
        }

        return offers.stream()
                .map(OfferResponse::from)
                .toList();
    }

    public OfferResponse updateOffer(
            Long offerId,
            String currentEmail,
            UpdateOfferRequest request
    ) {

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Oferta nie została znaleziona"
                        )
                );

        if (!offer.getUser().getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException(
                    "Nie możesz edytować cudzej oferty"
            );
        }

        Category category = categoryRepository.findById(
                        request.categoryId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Kategoria nie została znaleziona"
                        )
                );

        offer.setTitle(request.title());
        offer.setDescription(request.description());
        offer.setPrice(request.price());
        offer.setType(request.type());
        offer.setStatus(request.status());
        offer.setCategory(category);

        Offer savedOffer = offerRepository.save(offer);

        return OfferResponse.from(savedOffer);
    }

    public void deleteOffer(
            Long offerId,
            String currentEmail
    ) {

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Oferta nie została znaleziona"
                        )
                );

        if (!offer.getUser().getEmail().equals(currentEmail)) {
            throw new IllegalArgumentException(
                    "Nie możesz usunąć cudzej oferty"
            );
        }

        offerRepository.delete(offer);
    }
}