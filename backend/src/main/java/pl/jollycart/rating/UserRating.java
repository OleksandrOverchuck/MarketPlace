package pl.jollycart.rating;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import pl.jollycart.user.User;

/**
 * Ocena (1-5 gwiazdek) wystawiona przez jednego użytkownika drugiemu.
 * Każdy użytkownik może ocenić danego użytkownika tylko raz
 * (kolejna ocena nadpisuje poprzednią).
 */
@Entity
@Table(
        name = "user_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_ratings_rater_rated",
                columnNames = {"rater_id", "rated_user_id"}
        )
)
public class UserRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rated_user_id", nullable = false)
    private User ratedUser;

    @Column(nullable = false)
    private int stars;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserRating() {
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

    public User getRater() { return rater; }
    public void setRater(User rater) { this.rater = rater; }

    public User getRatedUser() { return ratedUser; }
    public void setRatedUser(User ratedUser) { this.ratedUser = ratedUser; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
