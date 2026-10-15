package pl.jollycart.rating;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.jollycart.offer.OfferRepository;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.dto.OfferResponse;
import pl.jollycart.rating.dto.RatingSummaryResponse;
import pl.jollycart.rating.dto.UserProfileResponse;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
public class UserRatingService {

    private final UserRatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;

    public UserRatingService(
            UserRatingRepository ratingRepository,
            UserRepository userRepository,
            OfferRepository offerRepository
    ) {
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
    }

    /** viewerEmail może być null (niezalogowany). */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId, String viewerEmail) {
        User user = findUser(userId);
        User viewer = findViewer(viewerEmail);

        RatingSummaryResponse summary = summary(user.getId(), viewer);

        List<OfferResponse> offers = offerRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(o -> o.getStatus() == OfferStatus.ACTIVE)
                .map(OfferResponse::from)
                .toList();

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getLocation(),
                user.getCreatedAt(),
                summary.averageRating(),
                summary.ratingCount(),
                summary.myRating(),
                viewer != null && viewer.getId().equals(user.getId()),
                offers
        );
    }

    @Transactional
    public RatingSummaryResponse rate(Long userId, String raterEmail, int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Ocena musi być od 1 do 5");
        }

        User rated = findUser(userId);
        User rater = findViewer(raterEmail);

        if (rater == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        if (rater.getId().equals(rated.getId())) {
            throw new IllegalArgumentException("Nie możesz ocenić samego siebie");
        }

        UserRating rating = ratingRepository
                .findByRaterIdAndRatedUserId(rater.getId(), rated.getId())
                .orElseGet(() -> {
                    UserRating r = new UserRating();
                    r.setRater(rater);
                    r.setRatedUser(rated);
                    return r;
                });

        rating.setStars(stars);
        ratingRepository.save(rating);

        return summary(rated.getId(), rater);
    }

    @Transactional
    public RatingSummaryResponse removeRating(Long userId, String raterEmail) {
        User rated = findUser(userId);
        User rater = findViewer(raterEmail);

        if (rater == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        ratingRepository.deleteByRaterIdAndRatedUserId(rater.getId(), rated.getId());

        return summary(rated.getId(), rater);
    }

    private RatingSummaryResponse summary(Long ratedUserId, User viewer) {
        Double avg = ratingRepository.findAverageStars(ratedUserId);
        long count = ratingRepository.countByRatedUserId(ratedUserId);

        Integer mine = null;
        if (viewer != null) {
            mine = ratingRepository
                    .findByRaterIdAndRatedUserId(viewer.getId(), ratedUserId)
                    .map(UserRating::getStars)
                    .orElse(null);
        }

        double rounded = avg == null ? 0 : Math.round(avg * 10.0) / 10.0;

        return new RatingSummaryResponse(rounded, count, mine);
    }

    private User findUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Użytkownik nie został znaleziony"));

        if (user.getStatus() == AccountStatus.BANNED) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Użytkownik nie został znaleziony");
        }

        return user;
    }

    private User findViewer(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }
}
