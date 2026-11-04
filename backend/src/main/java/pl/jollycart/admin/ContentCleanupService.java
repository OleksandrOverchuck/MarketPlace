package pl.jollycart.admin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import pl.jollycart.file.FileService;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/**
 * Trwale usuwa ogłoszenia i konta razem ze WSZYSTKIM, co jest do nich
 * podpięte (klucze obce!): zdjęcia, aplikacje o pracę, CV, rozmowy,
 * wiadomości, załączniki, powiadomienia, avatar.
 *
 * Pliki z dysku są kasowane dopiero PO udanym commicie transakcji -
 * jeśli coś się wysypie, nie zostaną rekordy wskazujące na brakujące pliki.
 */
@Service
public class ContentCleanupService {

    private static final Logger log =
            LoggerFactory.getLogger(ContentCleanupService.class);

    private static final String AVATAR_PREFIX = "/api/users/avatar/";

    @PersistenceContext
    private EntityManager em;

    private final FileService fileService;
    private final UserRepository userRepository;

    public ContentCleanupService(
            FileService fileService,
            UserRepository userRepository
    ) {
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    /* =========================
       OGŁOSZENIE
       ========================= */

    @Transactional
    public void deleteOffer(Long offerId) {

        Set<String> files = new LinkedHashSet<>();

        removeOfferDependencies(List.of(offerId), files);

        em.createQuery("delete from Offer o where o.id in :ids")
                .setParameter("ids", List.of(offerId))
                .executeUpdate();

        deleteFilesAfterCommit(files);
    }

    /* =========================
       UŻYTKOWNIK
       ========================= */

    @Transactional
    public void deleteUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        Set<String> files = new LinkedHashSet<>();

        List<Long> offerIds = em
                .createQuery(
                        "select o.id from Offer o where o.user.id = :uid",
                        Long.class
                )
                .setParameter("uid", userId)
                .getResultList();

        List<Long> conversationIds = em
                .createQuery(
                        "select cp.conversation.id "
                                + "from ConversationParticipant cp "
                                + "where cp.user.id = :uid",
                        Long.class
                )
                .setParameter("uid", userId)
                .getResultList();

        // 1) powiadomienia, w których użytkownik jest nadawcą / odbiorcą
        em.createQuery(
                        "delete from Notification n "
                                + "where n.recipient.id = :uid "
                                + "or n.sender.id = :uid"
                )
                .setParameter("uid", userId)
                .executeUpdate();

        // 2) rozmowy użytkownika: powiadomienia -> wiadomości ->
        //    załączniki -> uczestnicy -> rozmowy
        if (!conversationIds.isEmpty()) {

            em.createQuery(
                            "delete from Notification n "
                                    + "where n.conversation.id in :ids"
                    )
                    .setParameter("ids", conversationIds)
                    .executeUpdate();

            List<Long> attachmentIds = em
                    .createQuery(
                            "select distinct m.attachment.id from Message m "
                                    + "where m.conversation.id in :ids "
                                    + "and m.attachment is not null",
                            Long.class
                    )
                    .setParameter("ids", conversationIds)
                    .getResultList();

            em.createQuery(
                            "delete from Message m "
                                    + "where m.conversation.id in :ids"
                    )
                    .setParameter("ids", conversationIds)
                    .executeUpdate();

            deleteStoredFiles(attachmentIds, files);

            em.createQuery(
                            "delete from ConversationParticipant cp "
                                    + "where cp.conversation.id in :ids"
                    )
                    .setParameter("ids", conversationIds)
                    .executeUpdate();

            em.createQuery(
                            "delete from Conversation c "
                                    + "where c.id in :ids"
                    )
                    .setParameter("ids", conversationIds)
                    .executeUpdate();
        }

        // 3) aplikacje o pracę złożone PRZEZ użytkownika (+ ich CV)
        deleteJobApplications(
                "j.user.id = :uid",
                "uid",
                userId,
                files
        );

        // 4) ogłoszenia użytkownika (zdjęcia, aplikacje na nie, CV)
        if (!offerIds.isEmpty()) {
            removeOfferDependencies(offerIds, files);

            em.createQuery("delete from Offer o where o.id in :ids")
                    .setParameter("ids", offerIds)
                    .executeUpdate();
        }

        // 5) avatar zapisany lokalnie
        String avatarUrl = user.getAvatarUrl();

        if (avatarUrl != null && avatarUrl.startsWith(AVATAR_PREFIX)) {
            String stored = avatarUrl.substring(AVATAR_PREFIX.length());

            if (!stored.isBlank()) {
                files.add(stored);
            }
        }

        // 6) samo konto
        em.createQuery("delete from User u where u.id = :uid")
                .setParameter("uid", userId)
                .executeUpdate();

        em.clear();

        deleteFilesAfterCommit(files);
    }

    /* =========================
       POMOCNICZE
       ========================= */

    private void removeOfferDependencies(
            List<Long> offerIds,
            Set<String> files
    ) {
        // zdjęcia
        files.addAll(
                em.createQuery(
                                "select i.storedFileName from OfferImage i "
                                        + "where i.offer.id in :ids",
                                String.class
                        )
                        .setParameter("ids", offerIds)
                        .getResultList()
        );

        em.createQuery("delete from OfferImage i where i.offer.id in :ids")
                .setParameter("ids", offerIds)
                .executeUpdate();

        // aplikacje o pracę na to ogłoszenie (+ CV)
        deleteJobApplications(
                "j.offer.id in :ids",
                "ids",
                offerIds,
                files
        );
    }

    private void deleteJobApplications(
            String where,
            String parameterName,
            Object parameterValue,
            Set<String> files
    ) {
        List<Long> cvIds = em
                .createQuery(
                        "select distinct j.cvFile.id from JobApplication j "
                                + "where " + where
                                + " and j.cvFile is not null",
                        Long.class
                )
                .setParameter(parameterName, parameterValue)
                .getResultList();

        em.createQuery("delete from JobApplication j where " + where)
                .setParameter(parameterName, parameterValue)
                .executeUpdate();

        deleteStoredFiles(cvIds, files);
    }

    private void deleteStoredFiles(
            List<Long> storedFileIds,
            Set<String> files
    ) {
        if (storedFileIds.isEmpty()) {
            return;
        }

        files.addAll(
                em.createQuery(
                                "select f.storedFileName from StoredFile f "
                                        + "where f.id in :ids",
                                String.class
                        )
                        .setParameter("ids", storedFileIds)
                        .getResultList()
        );

        em.createQuery("delete from StoredFile f where f.id in :ids")
                .setParameter("ids", storedFileIds)
                .executeUpdate();
    }

    private void deleteFilesAfterCommit(Set<String> files) {

        if (files.isEmpty()) {
            return;
        }

        Runnable cleanup = () -> {
            for (String file : files) {
                try {
                    fileService.deleteFile(file);
                } catch (RuntimeException e) {
                    log.warn("Nie udało się usunąć pliku {}", file, e);
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cleanup.run();
                        }
                    }
            );
        } else {
            cleanup.run();
        }
    }
}
