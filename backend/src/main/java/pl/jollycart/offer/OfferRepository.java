package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import pl.jollycart.user.AccountStatus;

public interface OfferRepository
        extends JpaRepository<Offer, Long>, JpaSpecificationExecutor<Offer> {

    // Blokada wiersza na czas rezerwacji - dwóch kupujących naraz
    // nie przejdzie przez "kup teraz" dla tej samej oferty.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") Long id);

    List<Offer> findAllByOrderByCreatedAtDesc();

    List<Offer> findByStatusOrderByCreatedAtDesc(
            OfferStatus status
    );

    List<Offer> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

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

    long countByStatus(
            OfferStatus status
    );

    long countByStatusAndUserStatus(
            OfferStatus status,
            AccountStatus userStatus
    );

    long countByUserId(Long userId);
}