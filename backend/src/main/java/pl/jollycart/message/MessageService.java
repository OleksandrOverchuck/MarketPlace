package pl.jollycart.message;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.message.dto.CreateMessageRequest;
import pl.jollycart.message.dto.MessageResponse;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            UserRepository userRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    public MessageResponse createMessage(
            Long conversationId,
            String currentEmail,
            CreateMessageRequest request
    ) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );

        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Rozmowa nie została znaleziona"
                        )
                );

        boolean participant =
                participantRepository
                        .existsByConversationIdAndUserId(
                                conversationId,
                                currentUser.getId()
                        );

        if (!participant) {
            throw new IllegalArgumentException(
                    "Nie masz dostępu do tej rozmowy"
            );
        }

        Message message = new Message();

        message.setConversation(conversation);
        message.setSender(currentUser);
        message.setContent(request.content());

        Message savedMessage =
                messageRepository.save(message);

        return MessageResponse.from(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversationMessages(
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

        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException(
                    "Rozmowa nie została znaleziona"
            );
        }

        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(
                        conversationId
                )
                .stream()
                .map(MessageResponse::from)
                .toList();
    }
}