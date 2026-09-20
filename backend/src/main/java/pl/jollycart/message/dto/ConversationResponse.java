package pl.jollycart.message.dto;

import pl.jollycart.message.Conversation;
import pl.jollycart.message.ConversationParticipant;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
        Long id,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ParticipantResponse> participants
) {

    public static ConversationResponse from(
            Conversation conversation
    ) {
        List<ParticipantResponse> participants =
                conversation.getParticipants()
                        .stream()
                        .map(ParticipantResponse::from)
                        .toList();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                participants
        );
    }

    public record ParticipantResponse(
            Long userId,
            String nickname,
            String avatarUrl
    ) {

        public static ParticipantResponse from(
                ConversationParticipant participant
        ) {
            return new ParticipantResponse(
                    participant.getUser().getId(),
                    participant.getUser().getNickname(),
                    participant.getUser().getAvatarUrl()
            );
        }
    }
}