package pl.jollycart.message.dto;

import pl.jollycart.message.Message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderNickname,
        String senderAvatarUrl,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getSender().getAvatarUrl(),
                message.getContent(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}