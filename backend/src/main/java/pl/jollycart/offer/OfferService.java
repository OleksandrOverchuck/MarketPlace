package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        offer.setLocation(request.location());
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
    public List<OfferResponse> getOffersByUserEmail(
            String email
    ) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        return offerRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
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
        String location,
        Long categoryId,
        OfferType type,
        BigDecimal minPrice,
        BigDecimal maxPrice
    ) {

    Specification<Offer> specification =
            (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("status"),
                            OfferStatus.ACTIVE
                    );

    /*
     * WYSZUKIWANIE TEKSTOWE
     *
     * Szukamy w:
     * - tytule
     * - opisie
     *
     * Tytuł LUB opis.
     */
    if (search != null && !search.isBlank()) {

        String searchPattern =
                "%" + search.trim().toLowerCase() + "%";

        specification = specification.and(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.or(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get("title")
                                        ),
                                        searchPattern
                                ),
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                root.get("description")
                                        ),
                                        searchPattern
                                )
                        )
        );
    }

    /*
     * LOKALIZACJA
     *
     * Np.:
     * Opole
     * opole
     * Opol
     *
     * Wszystko będzie działało bez rozróżniania wielkości liter.
     */
    if (location != null && !location.isBlank()) {

        String locationPattern =
                "%" + location.trim().toLowerCase() + "%";

        specification = specification.and(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.like(
                                criteriaBuilder.lower(
                                        root.get("location")
                                ),
                                locationPattern
                        )
        );
    }

    /*
     * KATEGORIA
     */
    if (categoryId != null) {

        specification = specification.and(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(
                                root.get("category").get("id"),
                                categoryId
                        )
        );
    }

    /*
     * TYP OGŁOSZENIA
     */
    if (type != null) {

        specification = specification.and(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(
                                root.get("type"),
                                type
                        )
        );
    }

    /*
     * CENA MINIMALNA
     */
    if (minPrice != null) {

        specification = specification.and(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("price"),
                                minPrice
                        )
        );
    }

    /*
     * CENA MAKSYMALNA
     */
    if (maxPrice != null) {

        specification = specification.and(
                (root, query, criteriaBuilder) ->
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("price"),
                                maxPrice
                        )
        );
    }

    /*
     * POBIERAMY TYLKO AKTYWNE OGŁOSZENIA
     * I SORTUJEMY OD NAJNOWSZYCH.
     */
    List<Offer> offers = offerRepository.findAll(
            specification,
            Sort.by(
                    Sort.Direction.DESC,
                    "createdAt"
            )
    );

    return offers.stream()
            .map(OfferResponse::from)
            .toList();
}

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
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
        offer.setLocation(request.location());
        offer.setType(request.type());
        offer.setStatus(request.status());
        offer.setCategory(category);

        Offer savedOffer = offerRepository.save(offer);

        return OfferResponse.from(savedOffer);
    }

    @Transactional(readOnly = true)
    public long countActiveOffers() {
        return offerRepository.countByStatus(OfferStatus.ACTIVE);
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