package pl.jollycart.message;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import pl.jollycart.file.ChatAttachmentValidator;
import pl.jollycart.file.FileService;
import pl.jollycart.file.StoredFile;
import pl.jollycart.file.StoredFileRepository;
import pl.jollycart.message.dto.CreateMessageRequest;
import pl.jollycart.message.dto.MessageResponse;
import pl.jollycart.notification.NotificationService;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

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

    @Mock
    private NotificationService notificationService;

    @Mock
    private FileService fileService;

    @Mock
    private StoredFileRepository storedFileRepository;

    // prawdziwy walidator - nie ma zależności, a chcemy przetestować reguły
    private final ChatAttachmentValidator attachmentValidator =
            new ChatAttachmentValidator();

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                conversationRepository,
                participantRepository,
                userRepository,
                notificationService,
                fileService,
                storedFileRepository,
                attachmentValidator
        );
    }

    /* =====================================================
       WIADOMOŚCI TEKSTOWE
       ===================================================== */

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
        assertNull(response.attachment());

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

        verify(messageRepository, never())
                .save(any(Message.class));
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
        assertEquals(
                "Pierwsza wiadomość",
                responses.get(0).content()
        );
    }

    /* =====================================================
       ZAŁĄCZNIKI
       ===================================================== */

    private User participantUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setNickname("TestUser");
        return user;
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        return conversation;
    }

    private void stubParticipant(User user, boolean isParticipant) {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(conversationRepository.findById(10L))
                .thenReturn(Optional.of(conversation()));

        when(participantRepository.existsByConversationIdAndUserId(
                10L,
                user.getId()
        )).thenReturn(isParticipant);
    }

    @Test
    void shouldCreateAttachmentMessageForParticipant() {

        User user = participantUser();

        stubParticipant(user, true);

        when(fileService.storeFile(any()))
                .thenReturn("stored-uuid.pdf");

        when(storedFileRepository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> {
                    StoredFile file = invocation.getArgument(0);
                    file.setId(5L);
                    return file;
                });

        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> {
                    Message message = invocation.getArgument(0);
                    message.setId(1L);
                    return message;
                });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "umowa.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes()
        );

        MessageResponse response =
                messageService.createAttachmentMessage(
                        10L,
                        "test@example.com",
                        file,
                        null
                );

        assertEquals(1L, response.id());
        assertEquals("", response.content());

        assertNotNull(response.attachment());
        assertEquals(5L, response.attachment().id());
        assertEquals("umowa.pdf", response.attachment().fileName());
        assertEquals(
                "application/pdf",
                response.attachment().contentType()
        );
        assertEquals(
                file.getSize(),
                response.attachment().fileSize()
        );

        verify(notificationService)
                .createMessageNotifications(any(Message.class));
    }

    @Test
    void shouldRejectAttachmentFromNonParticipant() {

        User user = participantUser();

        stubParticipant(user, false);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "umowa.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes()
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> messageService.createAttachmentMessage(
                                10L,
                                "test@example.com",
                                file,
                                null
                        )
                );

        assertEquals(
                "Nie masz dostępu do tej rozmowy",
                exception.getMessage()
        );

        verify(fileService, never()).storeFile(any());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void shouldRejectAttachmentLargerThanFiveMegabytes() {

        User user = participantUser();

        stubParticipant(user, true);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "duzy.pdf",
                "application/pdf",
                new byte[5 * 1024 * 1024 + 1]
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> messageService.createAttachmentMessage(
                                10L,
                                "test@example.com",
                                file,
                                null
                        )
                );

        assertEquals(
                "Plik nie może być większy niż 5 MB",
                exception.getMessage()
        );

        verify(fileService, never()).storeFile(any());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void shouldRejectAttachmentWithDisallowedExtension() {

        User user = participantUser();

        stubParticipant(user, true);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "program.exe",
                "application/octet-stream",
                "MZ".getBytes()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.createAttachmentMessage(
                        10L,
                        "test@example.com",
                        file,
                        null
                )
        );

        verify(fileService, never()).storeFile(any());
    }

    @Test
    void shouldRejectAttachmentWhoseContentDoesNotMatchExtension() {

        User user = participantUser();

        stubParticipant(user, true);

        // plik .pdf, ale w środku nie ma sygnatury PDF
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "falszywy.pdf",
                "application/pdf",
                "to nie jest pdf".getBytes()
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> messageService.createAttachmentMessage(
                                10L,
                                "test@example.com",
                                file,
                                null
                        )
                );

        assertEquals(
                "Zawartość pliku nie pasuje do jego rozszerzenia",
                exception.getMessage()
        );

        verify(fileService, never()).storeFile(any());
    }

    @Test
    void shouldDeleteStoredFileWhenSavingMessageFails() {

        User user = participantUser();

        stubParticipant(user, true);

        when(fileService.storeFile(any()))
                .thenReturn("stored-uuid.pdf");

        when(storedFileRepository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(messageRepository.save(any(Message.class)))
                .thenThrow(new IllegalStateException("Błąd bazy"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "umowa.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes()
        );

        assertThrows(
                IllegalStateException.class,
                () -> messageService.createAttachmentMessage(
                        10L,
                        "test@example.com",
                        file,
                        null
                )
        );

        // żeby nie zostawał plik-sierota na dysku
        verify(fileService).deleteFile("stored-uuid.pdf");
        verify(notificationService, never())
                .createMessageNotifications(any(Message.class));
    }
}
