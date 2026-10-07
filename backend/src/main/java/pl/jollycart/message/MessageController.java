package pl.jollycart.message;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import pl.jollycart.message.dto.CreateMessageRequest;
import pl.jollycart.message.dto.MessageResponse;

@RestController
@RequestMapping("/api/conversations")
public class MessageController {

    private final MessageService messageService;

    public MessageController(
            MessageService messageService
    ) {
        this.messageService = messageService;
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable Long conversationId,
            Authentication authentication,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        String currentEmail =
                authentication.getName();

        MessageResponse response =
                messageService.createMessage(
                        conversationId,
                        currentEmail,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        String currentEmail =
                authentication.getName();

        List<MessageResponse> messages =
                messageService.getConversationMessages(
                        conversationId,
                        currentEmail
                );

        return ResponseEntity.ok(messages);
    }

    /*
     * Wysłanie pliku (do 5 MB) w rozmowie.
     * "content" jest opcjonalnym podpisem pod plikiem.
     */
    @PostMapping(
            value = "/{conversationId}/messages/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<MessageResponse> createAttachmentMessage(
            @PathVariable Long conversationId,
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "content", required = false) String content
    ) {
        String currentEmail =
                authentication.getName();

        MessageResponse response =
                messageService.createAttachmentMessage(
                        conversationId,
                        currentEmail,
                        file,
                        content
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /*
     * Pobranie załącznika - dostępne tylko dla uczestników rozmowy.
     */
    @GetMapping("/{conversationId}/messages/{messageId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @RequestParam(value = "download", defaultValue = "false")
            boolean download,
            Authentication authentication
    ) {
        String currentEmail =
                authentication.getName();

        MessageService.AttachmentDownload attachment =
                messageService.getAttachment(
                        conversationId,
                        messageId,
                        currentEmail
                );

        String contentType =
                attachment.file().getContentType();

        boolean previewable =
                "application/pdf".equals(contentType)
                        || contentType.startsWith("image/");

        ContentDisposition disposition =
                (previewable && !download
                        ? ContentDisposition.inline()
                        : ContentDisposition.attachment())
                        .filename(
                                attachment.file().getOriginalFileName(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(attachment.file().getFileSize())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(attachment.resource());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "message",
                        exception.getMessage() != null
                                ? exception.getMessage()
                                : "Nieprawidłowe żądanie"
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "message",
                        "Plik nie może być większy niż 5 MB"
                ));
    }
}
