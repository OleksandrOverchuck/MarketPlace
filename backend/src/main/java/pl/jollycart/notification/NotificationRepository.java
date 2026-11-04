package pl.jollycart.notification;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(
            Long recipientId
    );

    long countByRecipientIdAndReadFalse(
            Long recipientId
    );

    boolean existsByRecipientIdAndConversationIdAndCreatedAtAfter(
            Long recipientId,
            Long conversationId,
            LocalDateTime after
    );
}