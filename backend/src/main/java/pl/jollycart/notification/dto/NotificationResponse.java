package pl.jollycart.notification.dto;

import java.time.LocalDateTime;

import pl.jollycart.notification.Notification;
import pl.jollycart.notification.NotificationType;

public record NotificationResponse(
        Long id,
        NotificationType type,
        boolean read,
        Long conversationId,
        Long messageId,
        Long senderId,
        String senderNickname,
        String senderAvatarUrl,
        String content,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(
            Notification notification
    ) {

        String content = notification.getMessage().getContent();

        if ((content == null || content.isBlank())
                && notification.getMessage().getAttachment() != null) {

            content = "\uD83D\uDCCE "
                    + notification.getMessage()
                            .getAttachment()
                            .getOriginalFileName();
        }

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.isRead(),
                notification.getConversation().getId(),
                notification.getMessage().getId(),
                notification.getSender().getId(),
                notification.getSender().getNickname(),
                notification.getSender().getAvatarUrl(),
                content,
                notification.getCreatedAt()
        );
    }
}