package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/*
 * Zamówienie = jedna transakcja kupna oferty przez JollyCart.
 *
 * (Nazwa klasy PurchaseOrder, bo "Order" jest słowem kluczowym JPQL.)
 *
 * Powiązania z ofertą i użytkownikami trzymamy jako same identyfikatory
 * + migawkę tytułu i kwoty. Dzięki temu historia płatności nie zniknie
 * ani nie zablokuje usunięcia ogłoszenia / konta (brak kluczy obcych),
 * a późniejsza edycja ceny w ogłoszeniu nie zmieni kwoty zamówienia.
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                @jakarta.persistence.Index(name = "idx_orders_status", columnList = "status"),
                @jakarta.persistence.Index(name = "idx_orders_buyer", columnList = "buyer_id"),
                @jakarta.persistence.Index(name = "idx_orders_seller", columnList = "seller_id")
        }
)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "offer_title", nullable = false, length = 150)
    private String offerTitle;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "stripe_session_id", unique = true, length = 255)
    private String stripeSessionId;

    @Column(name = "stripe_checkout_url", length = 2000)
    private String stripeCheckoutUrl;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id", length = 255)
    private String stripeChargeId;

    @Column(name = "stripe_transfer_id", length = 255)
    private String stripeTransferId;

    @Column(name = "checkout_expires_at")
    private LocalDateTime checkoutExpiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // Do kiedy sprzedawca musi wysłać towar
    @Column(name = "ship_deadline")
    private LocalDateTime shipDeadline;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // Do kiedy kupujący może potwierdzić / zgłosić problem
    @Column(name = "confirm_deadline")
    private LocalDateTime confirmDeadline;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PurchaseOrder() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }
    public String getOfferTitle() { return offerTitle; }
    public void setOfferTitle(String offerTitle) { this.offerTitle = offerTitle; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String v) { this.stripeSessionId = v; }
    public String getStripeCheckoutUrl() { return stripeCheckoutUrl; }
    public void setStripeCheckoutUrl(String v) { this.stripeCheckoutUrl = v; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String v) { this.stripePaymentIntentId = v; }
    public String getStripeChargeId() { return stripeChargeId; }
    public void setStripeChargeId(String v) { this.stripeChargeId = v; }
    public String getStripeTransferId() { return stripeTransferId; }
    public void setStripeTransferId(String v) { this.stripeTransferId = v; }
    public LocalDateTime getCheckoutExpiresAt() { return checkoutExpiresAt; }
    public void setCheckoutExpiresAt(LocalDateTime v) { this.checkoutExpiresAt = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { this.paidAt = v; }
    public LocalDateTime getShipDeadline() { return shipDeadline; }
    public void setShipDeadline(LocalDateTime v) { this.shipDeadline = v; }
    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime v) { this.shippedAt = v; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime v) { this.deliveredAt = v; }
    public LocalDateTime getConfirmDeadline() { return confirmDeadline; }
    public void setConfirmDeadline(LocalDateTime v) { this.confirmDeadline = v; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }
    public String getDisputeReason() { return disputeReason; }
    public void setDisputeReason(String v) { this.disputeReason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
