package pl.jollycart.dispute;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pl.jollycart.payment.PaymentGatewayException;

/* Czytelne komunikaty po polsku dla endpointów zgłoszeń (pakiet dispute). */
@RestControllerAdvice(basePackageClasses = DisputeExceptionHandler.class)
public class DisputeExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(DisputeExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Map<String, String>> gateway(PaymentGatewayException e) {
        log.error("Błąd operatora płatności przy rozstrzyganiu zgłoszenia", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", String.valueOf(e.getMessage())));
    }
}
