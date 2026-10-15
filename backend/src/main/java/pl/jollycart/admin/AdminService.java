package pl.jollycart.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.offer.OfferRepository;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/**
 * Operacje tylko dla ADMINA: trwałe usuwanie kont i nadawanie ról.
 */
@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final ContentCleanupService contentCleanupService;
    private final AuditService auditService;

    public AdminService(
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

    public void deleteUser(String actorEmail, Long targetId) {

        User actor = findActor(actorEmail);
        User target = findUser(targetId);

        assertTargetIsManageable(actor, target);

        String details = "Trwale usunięto konto "
                + target.getNickname()
                + " (" + target.getEmail() + ", id " + target.getId()
                + ", rola " + target.getRole() + ")";

        auditService.log(
                actor,
                AuditAction.USER_DELETED,
                "USER",
                target.getId(),
                details
        );

        contentCleanupService.deleteUser(targetId);
    }

    public StaffUserResponse changeRole(
            String actorEmail,
            Long targetId,
            Role newRole
    ) {
        User actor = findActor(actorEmail);
        User target = findUser(targetId);

        assertTargetIsManageable(actor, target);

        // Rolę ADMIN ma wyłącznie konto tworzone przy starcie aplikacji.
        if (newRole != Role.USER && newRole != Role.MODERATOR) {
            throw StaffOperationException.forbidden(
                    "Można nadać tylko rolę USER lub MODERATOR"
            );
        }

        Role oldRole = target.getRole();

        if (oldRole != newRole) {
            target.setRole(newRole);
            userRepository.save(target);

            auditService.log(
                    actor,
                    AuditAction.ROLE_CHANGED,
                    "USER",
                    target.getId(),
                    "Zmiana roli użytkownika " + target.getNickname()
                            + " (id " + target.getId() + "): "
                            + oldRole + " -> " + newRole
            );
        }

        return StaffUserResponse.from(
                target,
                offerRepository.countByUserId(target.getId())
        );
    }

    private void assertTargetIsManageable(User actor, User target) {

        if (actor.getId().equals(target.getId())) {
            throw StaffOperationException.forbidden(
                    "Nie możesz wykonać tej akcji na własnym koncie"
            );
        }

        if (target.getRole() == Role.ADMIN) {
            throw StaffOperationException.forbidden(
                    "Nie można zmieniać ani usuwać konta administratora"
            );
        }
    }

    private User findActor(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        StaffOperationException.forbidden(
                                "Nie znaleziono konta wykonującego akcję"
                        )
                );
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        StaffOperationException.notFound(
                                "Użytkownik nie został znaleziony"
                        )
                );
    }
}
