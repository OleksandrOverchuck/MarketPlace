package pl.jollycart.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // Blokada wiersza: webhook, scheduler i kupujący nie zmienią
    // tego samego zamówienia jednocześnie.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.stripeSessionId = :sessionId")
    Optional<PurchaseOrder> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    List<PurchaseOrder> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<PurchaseOrder> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<PurchaseOrder> findByStatusOrderByUpdatedAtAsc(OrderStatus status);

    // --- zapytania dla schedulera (tylko identyfikatory) ---

    @Query("select o.id from PurchaseOrder o where o.status = :status and o.checkoutExpiresAt < :now")
    List<Long> findIdsPendingExpired(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now);

    @Query("select o.id from PurchaseOrder o where o.status = :status and o.shipDeadline < :now")
    List<Long> findIdsShipDeadlinePassed(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now);

    @Query("select o.id from PurchaseOrder o where o.status = :status and o.shippedAt < :threshold")
    List<Long> findIdsShippedBefore(
            @Param("status") OrderStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Query("select o.id from PurchaseOrder o where o.status = :status and o.confirmDeadline < :now")
    List<Long> findIdsConfirmDeadlinePassed(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now);
}
