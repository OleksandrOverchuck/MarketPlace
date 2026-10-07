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
}
