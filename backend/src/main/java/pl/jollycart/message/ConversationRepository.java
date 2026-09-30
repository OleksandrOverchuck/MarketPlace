package pl.jollycart.message;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    @Query("""
        SELECT cp1.conversation
        FROM ConversationParticipant cp1
        JOIN ConversationParticipant cp2
            ON cp2.conversation = cp1.conversation
        WHERE cp1.user.id = :userId1
          AND cp2.user.id = :userId2
    """)
    Optional<Conversation> findConversationBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );
}