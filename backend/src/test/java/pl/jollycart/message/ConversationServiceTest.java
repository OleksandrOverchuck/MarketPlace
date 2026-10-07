package pl.jollycart.message;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository,
                participantRepository,
                userRepository
        );
    }

    @Test
    void shouldCreateConversationBetweenTwoUsers() {
        User first = new User();
        first.setId(1L);
        first.setEmail("first@example.com");

        User second = new User();
        second.setId(2L);
        second.setEmail("second@example.com");

        Conversation conversation = new Conversation();
        conversation.setId(1L);

        when(userRepository.findByEmail("first@example.com"))
                .thenReturn(Optional.of(first));
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(second));
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> {
                Conversation savedConversation =
                        invocation.getArgument(0);

                savedConversation.setId(1L);

                return savedConversation;
                });

        Conversation result =
                conversationService.createConversation(
                        "first@example.com",
                        2L,
                        100L
                );

        assertEquals(1L, result.getId());
        assertEquals(100L, result.getOfferId());
        verify(conversationRepository).save(any(Conversation.class));
        verify(participantRepository, times(2))
                .save(any(ConversationParticipant.class));
    }

    @Test
    void shouldRejectConversationWithSelf() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> conversationService.createConversation(
                                "test@example.com",
                                1L,
                                100L
                        )
                );

        assertEquals(
                "Nie możesz utworzyć rozmowy z samym sobą",
                exception.getMessage()
        );
        verify(conversationRepository, never())
                .save(any(Conversation.class));
    }

    @Test
    void shouldRejectConversationAccessForNonParticipant() {
        User user = new User();
        user.setId(3L);
        user.setEmail("third@example.com");

        when(userRepository.findByEmail("third@example.com"))
                .thenReturn(Optional.of(user));
        when(participantRepository.existsByConversationIdAndUserId(1L, 3L))
                .thenReturn(false);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> conversationService.getConversation(
                                1L,
                                "third@example.com"
                        )
                );

        assertEquals(
                "Nie masz dostępu do tej rozmowy",
                exception.getMessage()
        );
        verify(conversationRepository, never()).findById(1L);
    }
}
