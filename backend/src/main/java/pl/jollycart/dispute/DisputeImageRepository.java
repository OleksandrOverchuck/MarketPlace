package pl.jollycart.dispute;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeImageRepository extends JpaRepository<DisputeImage, Long> {

    List<DisputeImage> findByDisputeIdOrderByIdAsc(Long disputeId);

    long countByDisputeId(Long disputeId);

    Optional<DisputeImage> findByIdAndDisputeId(Long id, Long disputeId);
}
