package pl.jollycart.job;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByOfferIdAndUserId(
            Long offerId,
            Long userId
    );

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    List<JobApplication> findByOfferIdOrderByCreatedAtDesc(
            Long offerId
    );

    boolean existsByOfferIdAndUserId(
            Long offerId,
            Long userId
    );
}