package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.category.Category;
import pl.jollycart.category.CategoryRepository;
import pl.jollycart.offer.dto.CreateOfferRequest;
import pl.jollycart.offer.dto.OfferResponse;
import pl.jollycart.offer.dto.UpdateOfferRequest;
import pl.jollycart.payment.SellerAccountService;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class OfferService {

    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SellerAccountService sellerAccountService;

    @Autowired
    public OfferService(
            OfferRepository offerRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            SellerAccountService sellerAccountService
    ) {
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.sellerAccountService = sellerAccountService;
    }

    // Konstruktor bez płatności (używany m.in. w testach jednostkowych).
    public OfferService(
            OfferRepository offerRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository
    ) {
        this(offerRepository, userRepository, categoryRepository, null);
    }

    /*
     * Płatność przez JollyCart: tylko oferty SALE z ceną, a sprzedawca
     * musi mieć aktywne konto wypłat (dodane w ustawieniach profilu).
     */
    private boolean resolveOnlinePayment(
            Boolean requested,
            OfferType type,
            BigDecimal price,
            User seller
    ) {
        if (!Boolean.TRUE.equals(requested)) {
            return false;
        }

        if (type != OfferType.SALE) {
            throw new IllegalArgumentException(
                    "Płatność przez JollyCart jest dostępna tylko dla ofert sprzedaży"
            );
        }

        if (price == null || price.compareTo(new BigDecimal("2.00")) < 0) {
            throw new IllegalArgumentException(
                    "Płatność przez JollyCart wymaga ceny co najmniej 2,00 zł"
            );
        }

        if (sellerAccountService == null
                || !sellerAccountService.isPayoutReady(seller.getId())) {
            throw new IllegalArgumentException(
                    "Aby włączyć płatność przez JollyCart, dodaj konto bankowe w ustawieniach profilu"
            );
        }

        return true;
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
        offer.setOnlinePaymentEnabled(
                resolveOnlinePayment(
                        request.onlinePaymentEnabled(),
                        request.type(),
                        request.price(),
                        user
                )
        );

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

    // Tylko aktywne ogłoszenia użytkowników, którzy nie są zbanowani.
    Specification<Offer> specification =
            (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(
                            criteriaBuilder.equal(
                                    root.get("status"),
                                    OfferStatus.ACTIVE
                            ),
                            criteriaBuilder.equal(
                                    root.get("user").get("status"),
                                    AccountStatus.ACTIVE
                            )
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

        // Gdy trwa transakcja (RESERVED), oferta jest zamrożona -
        // inaczej sprzedawca mógłby ją zmienić po tym, jak ktoś zapłacił.
        if (offer.getStatus() == OfferStatus.RESERVED) {
            throw new IllegalArgumentException(
                    "Oferta jest w trakcie transakcji - nie można jej teraz edytować"
            );
        }

        if (request.status() == OfferStatus.RESERVED) {
            throw new IllegalArgumentException(
                    "Status \"zarezerwowane\" ustawia system podczas płatności"
            );
        }

        // Brak pola w żądaniu = bez zmiany (ale tylko dla ofert SALE).
        boolean onlinePayment = request.onlinePaymentEnabled() == null
                ? offer.isOnlinePaymentEnabled() && request.type() == OfferType.SALE
                : resolveOnlinePayment(
                        request.onlinePaymentEnabled(),
                        request.type(),
                        request.price(),
                        offer.getUser()
                );

        offer.setTitle(request.title());
        offer.setDescription(request.description());
        offer.setPrice(request.price());
        offer.setLocation(request.location());
        offer.setType(request.type());
        offer.setStatus(request.status());
        offer.setCategory(category);
        offer.setOnlinePaymentEnabled(onlinePayment);

        Offer savedOffer = offerRepository.save(offer);

        return OfferResponse.from(savedOffer);
    }

    @Transactional(readOnly = true)
    public long countActiveOffers() {
        return offerRepository.countByStatusAndUserStatus(
                OfferStatus.ACTIVE,
                AccountStatus.ACTIVE
        );
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

        if (offer.getStatus() == OfferStatus.RESERVED) {
            throw new IllegalArgumentException(
                    "Oferta jest w trakcie transakcji - nie można jej teraz usunąć"
            );
        }

        offerRepository.delete(offer);
    }
}