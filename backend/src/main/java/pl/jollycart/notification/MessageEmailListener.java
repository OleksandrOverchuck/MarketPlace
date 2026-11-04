package pl.jollycart.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import pl.jollycart.auth.EmailService;

/*
 * Wysyła maila o nowej wiadomości dopiero PO zatwierdzeniu transakcji
 * (jeśli zapis wiadomości się nie uda, mail nie wyjdzie)
 * i asynchronicznie (SMTP nie spowalnia wysyłania wiadomości w czacie).
 * Błąd wysyłki maila jest tylko logowany - nie psuje czatu.
 */
@Component
public class MessageEmailListener {

    private static final Logger log =
            LoggerFactory.getLogger(MessageEmailListener.class);

    private final EmailService emailService;
    private final String frontendUrl;

    public MessageEmailListener(
            EmailService emailService,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageEmailEvent event) {

        String chatUrl = frontendUrl
                + "/frontend/pages/chat.html?conversationId="
                + event.conversationId()
                + "&from=conversations";

        try {
            emailService.sendNewMessageNotification(
                    event.recipientEmail(),
                    event.recipientNickname(),
                    event.senderNickname(),
                    event.text(),
                    event.attachmentName(),
                    chatUrl
            );
        } catch (RuntimeException e) {
            log.warn(
                    "Nie udało się wysłać maila o nowej wiadomości (rozmowa {})",
                    event.conversationId(),
                    e
            );
        }
    }
}
