package pl.jollycart.dispute;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderDisputeRepository extends JpaRepository<OrderDispute, Long> {

    // Blokada wiersza: dwóch moderatorów nie rozstrzygnie tego samego zgłoszenia naraz.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from OrderDispute d where d.id = :id")
    Optional<OrderDispute> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrderId(Long orderId);

    long countByStatus(DisputeStatus status);

    // Aktualne: najstarsze na górze (czekają najdłużej)
    List<OrderDispute> findByStatusOrderByCreatedAtAsc(DisputeStatus status);

    // Rozwiązane: najnowsze na górze
    List<OrderDispute> findTop100ByStatusOrderByResolvedAtDesc(DisputeStatus status);
}
