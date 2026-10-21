package pl.jollycart.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.stripe.Stripe;

import jakarta.annotation.PostConstruct;

@Configuration
public class StripeConfig {

    private final String secretKey;

    public StripeConfig(@Value("${stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    @PostConstruct
    void init() {
        if (secretKey == null || secretKey.isBlank()) {
            return; // aplikacja działa, ale bez płatności
        }

        // Zabezpieczenie: tylko klucz testowy
        if (!secretKey.startsWith("sk_test_") && !secretKey.startsWith("rk_test_")) {
            throw new IllegalStateException(
                    "STRIPE_SECRET_KEY musi być kluczem testowym (sk_test_...).");
        }

        Stripe.apiKey = secretKey;
    }

    public static void assertConfigured() {
        if (Stripe.apiKey == null || Stripe.apiKey.isBlank()) {
            throw new PaymentGatewayException(
                    "Płatności nie są skonfigurowane (brak STRIPE_SECRET_KEY).");
        }
    }
}