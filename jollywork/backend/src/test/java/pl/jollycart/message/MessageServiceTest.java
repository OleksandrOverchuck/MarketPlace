package pl.jollycart.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.jollycart.message.dto.CreateMessageRequest;
import pl.jollycart.message.dto.MessageResponse;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                conversationRepository,
                participantRepository,
                userRepository
        );
    }

    @Test
    void shouldCreateMessageForParticipant() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setNickname("TestUser");

        Conversation conversation = new Conversation();
        conversation.setId(10L);

        CreateMessageRequest request =
                new CreateMessageRequest("Cześć!");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(conversationRepository.findById(10L))
                .thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(10L, 1L))
                .thenReturn(true);
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> {
                    Message message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        MessageResponse response =
                messageService.createMessage(
                        10L,
                        "test@example.com",
                        request
                );

        assertEquals(1L, response.id());
        assertEquals(10L, response.conversationId());
        assertEquals(1L, response.senderId());
        assertEquals("TestUser", response.senderNickname());
        assertEquals("Cześć!", response.content());

        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void shouldRejectMessageFromNonParticipant() {
        User user = new User();
        user.setId(3L);
        user.setEmail("third@example.com");

        Conversation conversation = new Conversation();
        conversation.setId(10L);

        when(userRepository.findByEmail("third@example.com"))
                .thenReturn(Optional.of(user));
        when(conversationRepository.findById(10L))
                .thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(10L, 3L))
                .thenReturn(false);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> messageService.createMessage(
                                10L,
                                "third@example.com",
                                new CreateMessageRequest("Hej")
                        )
                );

        assertEquals(
                "Nie masz dostępu do tej rozmowy",
                exception.getMessage()
        );
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void shouldGetConversationMessagesForParticipant() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setNickname("TestUser");

        Conversation conversation = new Conversation();
        conversation.setId(10L);

        Message message = new Message();
        message.setId(1L);
        message.setConversation(conversation);
        message.setSender(user);
        message.setContent("Pierwsza wiadomość");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(participantRepository.existsByConversationIdAndUserId(10L, 1L))
                .thenReturn(true);
        when(conversationRepository.existsById(10L))
                .thenReturn(true);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(message));

        List<MessageResponse> responses =
                messageService.getConversationMessages(
                        10L,
                        "test@example.com"
                );

        assertEquals(1, responses.size());
        assertEquals("Pierwsza wiadomość", responses.get(0).content());
    }
}
