package pl.jollycart.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.message.ConversationParticipant;
import pl.jollycart.message.Message;
import pl.jollycart.notification.dto.NotificationResponse;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class NotificationService {

    private static final long EMAIL_COOLDOWN_MINUTES = 10;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /*
     * Tworzymy powiadomienie dla każdego uczestnika rozmowy,
     * który NIE jest autorem wiadomości.
     */
    public void createMessageNotifications(Message message) {

        for (ConversationParticipant participant
                : message.getConversation().getParticipants()) {

            User recipient = participant.getUser();

            // Nie wysyłamy powiadomienia samemu sobie
            if (recipient.getId().equals(message.getSender().getId())) {
                continue;
            }

            /*
             * Najwyżej jeden mail na rozmowę w oknie EMAIL_COOLDOWN_MINUTES -
             * żeby żywa wymiana wiadomości nie zasypała skrzynki.
             */
            boolean shouldEmail =
                    !notificationRepository
                            .existsByRecipientIdAndConversationIdAndCreatedAtAfter(
                                    recipient.getId(),
                                    message.getConversation().getId(),
                                    LocalDateTime.now()
                                            .minusMinutes(EMAIL_COOLDOWN_MINUTES)
                            );

            Notification notification = new Notification();

            notification.setRecipient(recipient);
            notification.setSender(message.getSender());
            notification.setConversation(message.getConversation());
            notification.setMessage(message);
            notification.setType(NotificationType.MESSAGE);
            notification.setRead(false);

            notificationRepository.save(notification);

            if (shouldEmail) {
                eventPublisher.publishEvent(
                        new MessageEmailEvent(
                                recipient.getEmail(),
                                recipient.getNickname(),
                                message.getSender().getNickname(),
                                message.getConversation().getId(),
                                message.getContent(),
                                message.getAttachment() != null
                                        ? message.getAttachment()
                                                .getOriginalFileName()
                                        : null
                        )
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(
            String currentEmail
    ) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String currentEmail) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        return notificationRepository
                .countByRecipientIdAndReadFalse(user.getId());
    }

    public void markAsRead(
            Long notificationId,
            String currentEmail
    ) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        Notification notification =
                notificationRepository.findById(notificationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Powiadomienie nie zostało znalezione"
                                )
                        );

        if (!notification.getRecipient().getId()
                .equals(user.getId())) {

            throw new IllegalArgumentException(
                    "Nie masz dostępu do tego powiadomienia"
            );
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(String currentEmail) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .forEach(notification ->
                        notification.setRead(true)
                );
    }
}