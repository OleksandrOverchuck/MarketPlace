package pl.jollycart.notification;

/*
 * Dane potrzebne do wysłania maila o nowej wiadomości.
 * Same typy proste, bo mail wysyłamy w osobnym wątku,
 * poza transakcją (encje JPA byłyby tam już odłączone).
 */
public record MessageEmailEvent(
        String recipientEmail,
        String recipientNickname,
        String senderNickname,
        Long conversationId,
        String text,
        String attachmentName
) {
}
