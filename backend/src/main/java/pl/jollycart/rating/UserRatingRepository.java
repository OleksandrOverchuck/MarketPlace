package pl.jollycart.rating;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRatingRepository extends JpaRepository<UserRating, Long> {

    Optional<UserRating> findByRaterIdAndRatedUserId(Long raterId, Long ratedUserId);

    long countByRatedUserId(Long ratedUserId);

    @Query("select avg(r.stars) from UserRating r where r.ratedUser.id = :userId")
    Double findAverageStars(Long userId);

    void deleteByRaterIdAndRatedUserId(Long raterId, Long ratedUserId);
}
