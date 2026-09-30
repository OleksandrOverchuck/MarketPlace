package pl.jollycart.message;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    public Conversation createConversation(
            String currentEmail,
            Long otherUserId
    ) {

        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Drugi użytkownik nie został znaleziony"
                        )
                );

        if (currentUser.getId().equals(otherUser.getId())) {
            throw new IllegalArgumentException(
                    "Nie możesz utworzyć rozmowy z samym sobą"
            );
        }

        Conversation conversation = new Conversation();

        Conversation savedConversation =
                conversationRepository.save(conversation);

        ConversationParticipant firstParticipant =
                new ConversationParticipant();

        firstParticipant.setConversation(savedConversation);
        firstParticipant.setUser(currentUser);

        participantRepository.save(firstParticipant);

        ConversationParticipant secondParticipant =
                new ConversationParticipant();

        secondParticipant.setConversation(savedConversation);
        secondParticipant.setUser(otherUser);

        participantRepository.save(secondParticipant);

        savedConversation.getParticipants().add(firstParticipant);
        savedConversation.getParticipants().add(secondParticipant);

        return savedConversation;
    }

    @Transactional(readOnly = true)
    public List<Conversation> getUserConversations(
            String currentEmail
    ) {

        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        return participantRepository
                .findByUserId(currentUser.getId())
                .stream()
                .map(ConversationParticipant::getConversation)
                .toList();
    }

    @Transactional(readOnly = true)
    public Conversation getConversation(
            Long conversationId,
            String currentEmail
    ) {

        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        if (!participantRepository
                .existsByConversationIdAndUserId(
                        conversationId,
                        currentUser.getId()
                )) {

            throw new IllegalArgumentException(
                    "Nie masz dostępu do tej rozmowy"
            );
        }

        return conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Rozmowa nie została znaleziona"
                        )
                );
    }
}