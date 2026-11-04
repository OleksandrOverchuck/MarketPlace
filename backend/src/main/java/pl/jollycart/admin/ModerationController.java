package pl.jollycart.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.Role;

/**
 * Dostępne dla MODERATOR i ADMIN (patrz SecurityConfig).
 */
@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<StaffUserResponse>> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                moderationService.searchUsers(q, role, status, page, size)
        );
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<StaffUserResponse> getUser(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(moderationService.getUser(id));
    }

    @PostMapping("/users/{id}/ban")
    public ResponseEntity<StaffUserResponse> banUser(
            @PathVariable Long id,
            @Valid @RequestBody BanUserRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                moderationService.banUser(
                        authentication.getName(),
                        id,
                        request.reason()
                )
        );
    }

    @PostMapping("/users/{id}/unban")
    public ResponseEntity<StaffUserResponse> unbanUser(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                moderationService.unbanUser(
                        authentication.getName(),
                        id
                )
        );
    }

    @GetMapping("/offers")
    public ResponseEntity<PageResponse<StaffOfferResponse>> searchOffers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                moderationService.searchOffers(q, userId, status, page, size)
        );
    }

    @DeleteMapping("/offers/{id}")
    public ResponseEntity<Void> deleteOffer(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        moderationService.deleteOffer(
                authentication.getName(),
                id,
                reason
        );

        return ResponseEntity.noContent().build();
    }
}
