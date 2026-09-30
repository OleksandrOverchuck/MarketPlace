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
import jakarta.validation.constraints.NotNull;
import pl.jollycart.message.dto.ConversationResponse;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            Authentication authentication,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        String currentEmail = authentication.getName();

        Conversation conversation =
                conversationService.createConversation(
                        currentEmail,
                        request.otherUserId()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ConversationResponse.from(conversation));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations(
            Authentication authentication
    ) {
        String currentEmail = authentication.getName();

        List<ConversationResponse> conversations =
                conversationService
                        .getUserConversations(currentEmail)
                        .stream()
                        .map(ConversationResponse::from)
                        .toList();

        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String currentEmail = authentication.getName();

        Conversation conversation =
                conversationService.getConversation(
                        id,
                        currentEmail
                );

        return ResponseEntity.ok(
                ConversationResponse.from(conversation)
        );
    }

    public record CreateConversationRequest(
            @NotNull(message = "ID użytkownika jest wymagane")
            Long otherUserId
    ) {
    }
}