package pl.jollycart.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

/*
 * Endpoint, który wywołuje Stripe (serwer -> serwer), gdy coś się stanie
 * z płatnością. To jest źródło prawdy o tym, że kupujący zapłacił -
 * powrót przeglądarki na success_url niczego nie dowodzi.
 *
 * Bezpieczeństwo: endpoint jest publiczny (permitAll), więc KAŻDE żądanie
 * weryfikujemy podpisem z nagłówka Stripe-Signature i sekretem whsec_...
 * Treść musi być odczytana jako surowy String - jakakolwiek zmiana
 * (np. przez parsowanie JSON) unieważniłaby podpis.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentWebhookController.class);

    private final OrderService orderService;
    private final String webhookSecret;

    public PaymentWebhookController(
            OrderService orderService,
            @Value("${stripe.webhook-secret:}") String webhookSecret
    ) {
        this.orderService = orderService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Brak STRIPE_WEBHOOK_SECRET - odrzucam webhook");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("webhook not configured");
        }

        if (signature == null) {
            return ResponseEntity.badRequest().body("missing signature");
        }

        Event event;

        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook z nieprawidłowym podpisem");
            return ResponseEntity.badRequest().body("invalid signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                String sessionId = extractSessionId(event);
                if (sessionId != null) {
                    orderService.handleCheckoutCompleted(sessionId);
                }
            }
            case "checkout.session.expired" -> {
                String sessionId = extractSessionId(event);
                if (sessionId != null) {
                    orderService.handleCheckoutExpired(sessionId);
                }
            }
            default -> log.debug("Webhook: pomijam zdarzenie {}", event.getType());
        }

        // 2xx = "odebrałem"; przy wyjątku wyżej Stripe ponowi wysyłkę.
        return ResponseEntity.ok("ok");
    }

    private String extractSessionId(Event event) {

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        StripeObject object = deserializer.getObject().orElse(null);

        if (object == null) {
            // Wersja API zdarzenia różni się od wersji SDK
            try {
                object = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.error("Nie udało się odczytać obiektu zdarzenia {}", event.getId(), e);
                return null;
            }
        }

        return object instanceof Session session ? session.getId() : null;
    }
}
