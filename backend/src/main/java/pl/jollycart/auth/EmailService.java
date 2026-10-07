package pl.jollycart.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendVerificationCode(
            String to,
            String code,
            long validMinutes
    ) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        message.setSubject("JollyCart - kod weryfikacyjny");
        message.setText(
                "Twój kod weryfikacyjny: " + code + "\n\n"
                + "Kod jest ważny przez " + validMinutes + " minut.\n"
                + "Jeśli to nie Ty zakładasz konto w JollyCart, zignoruj tę wiadomość."
        );

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new EmailSendException(
                    "Nie udało się wysłać wiadomości e-mail. Spróbuj ponownie za chwilę.",
                    e
            );
        }
    }

    public void sendNewMessageNotification(
            String to,
            String recipientNickname,
            String senderNickname,
            String text,
            String attachmentName,
            String chatUrl
    ) {
        StringBuilder body = new StringBuilder();

        body.append("Cześć ").append(recipientNickname).append("!\n\n");
        body.append(senderNickname)
                .append(" wysłał(a) do Ciebie wiadomość w JollyCart:\n\n");

        if (text != null && !text.isBlank()) {
            String preview = text.length() > 500
                    ? text.substring(0, 500) + "..."
                    : text;

            body.append("\"").append(preview).append("\"\n");
        }

        if (attachmentName != null && !attachmentName.isBlank()) {
            body.append("Załącznik: ").append(attachmentName).append("\n");
        }

        body.append("\nOdpowiedz w czacie: ").append(chatUrl).append("\n\n");
        body.append("Aby nie zasypywać Twojej skrzynki, kolejne wiadomości ")
                .append("z tej rozmowy w ciągu kilku minut nie będą ")
                .append("powodować nowych maili.");

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(to);
        // usuwamy znaki nowej linii - ochrona przed wstrzyknięciem nagłówków
        message.setSubject(
                "JollyCart - nowa wiadomość od "
                        + senderNickname.replaceAll("[\\r\\n]", " ")
        );
        message.setText(body.toString());

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new EmailSendException(
                    "Nie udało się wysłać wiadomości e-mail.",
                    e
            );
        }
    }
}
