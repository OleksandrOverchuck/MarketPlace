package pl.jollycart.message;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pl.jollycart.file.ChatAttachmentValidator;
import pl.jollycart.file.FileService;
import pl.jollycart.file.StoredFile;
import pl.jollycart.file.StoredFileRepository;
import pl.jollycart.message.dto.CreateMessageRequest;
import pl.jollycart.message.dto.MessageResponse;
import pl.jollycart.notification.NotificationService;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FileService fileService;
    private final StoredFileRepository storedFileRepository;
    private final ChatAttachmentValidator attachmentValidator;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            FileService fileService,
            StoredFileRepository storedFileRepository,
            ChatAttachmentValidator attachmentValidator
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.fileService = fileService;
        this.storedFileRepository = storedFileRepository;
        this.attachmentValidator = attachmentValidator;
    }

    public MessageResponse createMessage(
            Long conversationId,
            String currentEmail,
            CreateMessageRequest request
    ) {

        User currentUser =
                userRepository.findByEmail(currentEmail)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Użytkownik nie został znaleziony"
                                )
                        );

        Conversation conversation =
                conversationRepository.findById(conversationId)
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

        /*
         * NOWE:
         * po zapisaniu wiadomości tworzymy
         * powiadomienie dla drugiego uczestnika.
         */
        notificationService.createMessageNotifications(
                savedMessage
        );

        return MessageResponse.from(savedMessage);
    }

    /*
     * Wiadomość z załącznikiem (do 5 MB).
     * Tekst jest opcjonalny - działa jak podpis pod plikiem.
     */
    public MessageResponse createAttachmentMessage(
            Long conversationId,
            String currentEmail,
            MultipartFile file,
            String content
    ) {

        User currentUser =
                userRepository.findByEmail(currentEmail)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Użytkownik nie został znaleziony"
                                )
                        );

        Conversation conversation =
                conversationRepository.findById(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Rozmowa nie została znaleziona"
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

        String caption = content == null ? "" : content.trim();

        if (caption.length() > 5000) {
            throw new IllegalArgumentException(
                    "Wiadomość może mieć maksymalnie 5000 znaków"
            );
        }

        ChatAttachmentValidator.ValidatedAttachment validated =
                attachmentValidator.validate(file);

        String storedFileName = fileService.storeFile(file);

        try {
            StoredFile storedFile = new StoredFile();

            storedFile.setOriginalFileName(validated.fileName());
            storedFile.setStoredFileName(storedFileName);
            storedFile.setContentType(validated.contentType());
            storedFile.setFileSize(file.getSize());

            storedFile = storedFileRepository.save(storedFile);

            Message message = new Message();

            message.setConversation(conversation);
            message.setSender(currentUser);
            message.setContent(caption);
            message.setAttachment(storedFile);

            Message savedMessage =
                    messageRepository.save(message);

            notificationService.createMessageNotifications(
                    savedMessage
            );

            return MessageResponse.from(savedMessage);

        } catch (RuntimeException e) {
            // żeby po nieudanym zapisie nie zostawał plik-sierota
            fileService.deleteFile(storedFileName);

            throw e;
        }
    }

    /*
     * Pobranie załącznika - tylko dla uczestników rozmowy.
     */
    @Transactional(readOnly = true)
    public AttachmentDownload getAttachment(
            Long conversationId,
            Long messageId,
            String currentEmail
    ) {

        User currentUser =
                userRepository.findByEmail(currentEmail)
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

        Message message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wiadomość nie została znaleziona"
                                )
                        );

        if (!message.getConversation().getId()
                .equals(conversationId)) {

            throw new IllegalArgumentException(
                    "Wiadomość nie należy do tej rozmowy"
            );
        }

        StoredFile attachment = message.getAttachment();

        if (attachment == null) {
            throw new IllegalArgumentException(
                    "Ta wiadomość nie ma załącznika"
            );
        }

        Resource resource =
                fileService.loadFile(attachment.getStoredFileName());

        return new AttachmentDownload(attachment, resource);
    }

    public record AttachmentDownload(
            StoredFile file,
            Resource resource
    ) {
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversationMessages(
            Long conversationId,
            String currentEmail
    ) {

        User currentUser =
                userRepository.findByEmail(currentEmail)
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