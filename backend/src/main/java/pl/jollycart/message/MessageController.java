package pl.jollycart.message;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.jollycart.message.dto.CreateMessageRequest;
import pl.jollycart.message.dto.MessageResponse;

@RestController
@RequestMapping("/api/conversations")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable Long conversationId,
            Authentication authentication,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        String currentEmail = authentication.getName();

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
        String currentEmail = authentication.getName();

        List<MessageResponse> messages =
                messageService.getConversationMessages(
                        conversationId,
                        currentEmail
                );

        return ResponseEntity.ok(messages);
    }
}