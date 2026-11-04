package pl.jollycart.rating;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import pl.jollycart.rating.dto.RateUserRequest;
import pl.jollycart.rating.dto.RatingSummaryResponse;
import pl.jollycart.rating.dto.UserProfileResponse;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserRatingService ratingService;

    public UserProfileController(UserRatingService ratingService) {
        this.ratingService = ratingService;
    }

    /** Publiczny profil - dostępny także bez logowania. */
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ratingService.getProfile(id, emailOf(authentication))
        );
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<RatingSummaryResponse> rate(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody RateUserRequest request
    ) {
        return ResponseEntity.ok(
                ratingService.rate(id, emailOf(authentication), request.stars())
        );
    }

    @DeleteMapping("/{id}/rating")
    public ResponseEntity<RatingSummaryResponse> removeRating(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ratingService.removeRating(id, emailOf(authentication))
        );
    }

    private String emailOf(Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(
            ResponseStatusException exception
    ) {
        String reason = exception.getReason() == null ? "" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", reason));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        errors.putIfAbsent("message", "Ocena musi być od 1 do 5");
        return ResponseEntity.badRequest().body(errors);
    }
}
