package pl.jollycart.admin;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/**
 * Operacje dostępne dla MODERATORA i ADMINA:
 * wyszukiwanie użytkowników, ban / unban, wyszukiwanie i usuwanie ogłoszeń.
 */
@Service
@Transactional
public class ModerationService {

    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final ContentCleanupService contentCleanupService;
    private final AuditService auditService;

    public ModerationService(
            UserRepository userRepository,
            OfferRepository offerRepository,
            ContentCleanupService contentCleanupService,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
        this.contentCleanupService = contentCleanupService;
        this.auditService = auditService;
    }

    /* =========================
       UŻYTKOWNICY
       ========================= */

    @Transactional(readOnly = true)
    public PageResponse<StaffUserResponse> searchUsers(
            String q,
            Role role,
            AccountStatus status,
            int page,
            int size
    ) {

        Specification<User> specification =
                (root, query, cb) -> cb.conjunction();

        if (q != null && !q.isBlank()) {

            String term = q.trim();
            String pattern =
                    "%" + escapeLike(term.toLowerCase()) + "%";

            boolean numeric = term.matches("\\d{1,18}");

            specification = specification.and(
                    (root, query, cb) -> {

                        Predicate byNickname = cb.like(
                                cb.lower(root.get("nickname")),
                                pattern,
                                '\\'
                        );

                        Predicate byEmail = cb.like(
                                cb.lower(root.get("email")),
                                pattern,
                                '\\'
                        );

                        if (numeric) {
                            Predicate byId = cb.equal(
                                    root.get("id"),
                                    Long.parseLong(term)
                            );

                            return cb.or(byId, byNickname, byEmail);
                        }

                        return cb.or(byNickname, byEmail);
                    }
            );
        }

        if (role != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("role"), role)
            );
        }

        if (status != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("status"), status)
            );
        }

        return PageResponse.of(
                userRepository.findAll(
                        specification,
                        PageRequest.of(
                                clampPage(page),
                                clampSize(size),
                                Sort.by(Sort.Direction.DESC, "id")
                        )
                ),
                this::toResponse
        );
    }

    @Transactional(readOnly = true)
    public StaffUserResponse getUser(Long id) {
        return toResponse(findUser(id));
    }

    public StaffUserResponse banUser(
            String actorEmail,
            Long targetId,
            String reason
    ) {
        User actor = findActor(actorEmail);
        User target = findUser(targetId);

        assertCanModerate(actor, target);

        if (target.isBanned()) {
            throw StaffOperationException.conflict(
                    "Użytkownik jest już zablokowany"
            );
        }

        target.setStatus(AccountStatus.BANNED);
        target.setBanReason(reason.trim());
        target.setBannedAt(LocalDateTime.now());
        target.setBannedById(actor.getId());

        User saved = userRepository.save(target);

        auditService.log(
                actor,
                AuditAction.USER_BANNED,
                "USER",
                saved.getId(),
                "Ban użytkownika " + saved.getNickname()
                        + " (" + saved.getEmail() + "). Powód: "
                        + reason.trim()
        );

        return toResponse(saved);
    }

    public StaffUserResponse unbanUser(
            String actorEmail,
            Long targetId
    ) {
        User actor = findActor(actorEmail);
        User target = findUser(targetId);

        assertCanModerate(actor, target);

        if (!target.isBanned()) {
            throw StaffOperationException.conflict(
                    "Użytkownik nie jest zablokowany"
            );
        }

        target.setStatus(AccountStatus.ACTIVE);
        target.setBanReason(null);
        target.setBannedAt(null);
        target.setBannedById(null);

        User saved = userRepository.save(target);

        auditService.log(
                actor,
                AuditAction.USER_UNBANNED,
                "USER",
                saved.getId(),
                "Odblokowano użytkownika " + saved.getNickname()
                        + " (" + saved.getEmail() + ")"
        );

        return toResponse(saved);
    }

    /* =========================
       OGŁOSZENIA
       ========================= */

    @Transactional(readOnly = true)
    public PageResponse<StaffOfferResponse> searchOffers(
            String q,
            Long userId,
            OfferStatus status,
            int page,
            int size
    ) {

        Specification<Offer> specification =
                (root, query, cb) -> cb.conjunction();

        if (q != null && !q.isBlank()) {

            String term = q.trim();
            String pattern =
                    "%" + escapeLike(term.toLowerCase()) + "%";

            boolean numeric = term.matches("\\d{1,18}");

            specification = specification.and(
                    (root, query, cb) -> {

                        Predicate byTitle = cb.like(
                                cb.lower(root.get("title")),
                                pattern,
                                '\\'
                        );

                        if (numeric) {
                            return cb.or(
                                    cb.equal(
                                            root.get("id"),
                                            Long.parseLong(term)
                                    ),
                                    byTitle
                            );
                        }

                        return byTitle;
                    }
            );
        }

        if (userId != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("user").get("id"),
                                    userId
                            )
            );
        }

        if (status != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("status"), status)
            );
        }

        return PageResponse.of(
                offerRepository.findAll(
                        specification,
                        PageRequest.of(
                                clampPage(page),
                                clampSize(size),
                                Sort.by(Sort.Direction.DESC, "id")
                        )
                ),
                StaffOfferResponse::from
        );
    }

    public void deleteOffer(
            String actorEmail,
            Long offerId,
            String reason
    ) {
        User actor = findActor(actorEmail);

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() ->
                        StaffOperationException.notFound(
                                "Ogłoszenie nie zostało znalezione"
                        )
                );

        User owner = offer.getUser();

        if (owner.getRole() == Role.ADMIN
                && actor.getRole() != Role.ADMIN) {
            throw StaffOperationException.forbidden(
                    "Nie możesz usuwać ogłoszeń administratora"
            );
        }

        String details = "Usunięto ogłoszenie \"" + offer.getTitle()
                + "\" użytkownika " + owner.getNickname()
                + " (id " + owner.getId() + ")";

        if (reason != null && !reason.isBlank()) {
            details += ". Powód: " + reason.trim();
        }

        auditService.log(
                actor,
                AuditAction.OFFER_DELETED,
                "OFFER",
                offerId,
                details
        );

        contentCleanupService.deleteOffer(offerId);
    }

    /* =========================
       POMOCNICZE
       ========================= */

    private void assertCanModerate(User actor, User target) {

        if (actor.getId().equals(target.getId())) {
            throw StaffOperationException.forbidden(
                    "Nie możesz wykonać tej akcji na własnym koncie"
            );
        }

        if (target.getRole() == Role.ADMIN) {
            throw StaffOperationException.forbidden(
                    "Nie można moderować administratora"
            );
        }

        if (target.getRole() == Role.MODERATOR
                && actor.getRole() != Role.ADMIN) {
            throw StaffOperationException.forbidden(
                    "Tylko administrator może moderować moderatora"
            );
        }
    }

    User findActor(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        StaffOperationException.forbidden(
                                "Nie znaleziono konta wykonującego akcję"
                        )
                );
    }

    User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        StaffOperationException.notFound(
                                "Użytkownik nie został znaleziony"
                        )
                );
    }

    StaffUserResponse toResponse(User user) {
        return StaffUserResponse.from(
                user,
                offerRepository.countByUserId(user.getId())
        );
    }

    private int clampPage(int page) {
        return Math.max(page, 0);
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
