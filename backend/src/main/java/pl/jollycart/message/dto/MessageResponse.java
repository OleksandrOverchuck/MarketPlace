package pl.jollycart.message.dto;

import java.time.LocalDateTime;

import pl.jollycart.file.StoredFile;
import pl.jollycart.message.Message;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderNickname,
        String senderAvatarUrl,
        String content,
        AttachmentResponse attachment,
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
                AttachmentResponse.from(message.getAttachment()),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }

    /*
     * Metadane załącznika. Sam plik pobierany jest z:
     * GET /api/conversations/{conversationId}/messages/{messageId}/attachment
     */
    public record AttachmentResponse(
            Long id,
            String fileName,
            String contentType,
            Long fileSize
    ) {

        public static AttachmentResponse from(StoredFile file) {

            if (file == null) {
                return null;
            }

            return new AttachmentResponse(
                    file.getId(),
                    file.getOriginalFileName(),
                    file.getContentType(),
                    file.getFileSize()
            );
        }
    }
}
