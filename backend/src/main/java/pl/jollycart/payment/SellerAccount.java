package pl.jollycart.payment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "seller_accounts")
public class SellerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "stripe_account_id", nullable = false, unique = true, length = 64)
    private String stripeAccountId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SellerAccount() {
    }

    public SellerAccount(Long userId, String stripeAccountId) {
        this.userId = userId;
        this.stripeAccountId = stripeAccountId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public String getStripeAccountId() { return stripeAccountId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}