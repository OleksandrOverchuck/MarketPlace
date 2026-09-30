package pl.jollycart.message;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, Long> {

    List<ConversationParticipant> findByUserId(Long userId);

    boolean existsByConversationIdAndUserId(
            Long conversationId,
            Long userId
    );
}