package pl.jollycart.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import pl.jollycart.auth.EmailService;
import pl.jollycart.payment.OrderPaidEvent;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/*
 * Po zatwierdzeniu transakcji, w której zamówienie zmieniło status na PAID,
 * wysyła sprzedawcy maila o opłaconym zamówieniu.
 *
 * Działa PO commit i asynchronicznie, więc ewentualny błąd SMTP jest tylko
 * logowany i nigdy nie cofa zapisu płatności.
 */
@Component
public class OrderPaidListener {

    private static final Logger log =
            LoggerFactory.getLogger(OrderPaidListener.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final String frontendUrl;

    public OrderPaidListener(
            UserRepository userRepository,
            EmailService emailService,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {

        User seller = userRepository.findById(event.sellerId()).orElse(null);
        User buyer = userRepository.findById(event.buyerId()).orElse(null);

        if (seller == null || buyer == null) {
            log.warn("Zamówienie {}: brak sprzedawcy lub kupującego - pomijam mail",
                    event.orderId());
            return;
        }

        try {
            emailService.sendOrderPaidNotification(
                    seller.getEmail(),
                    seller.getNickname(),
                    buyer.getNickname(),
                    event.offerTitle(),
                    event.amountText(),
                    event.shipWindowText(),
                    event.shipDeadlineText(),
                    frontendUrl + "/frontend/pages/orders.html?tab=sales"
            );
        } catch (RuntimeException e) {
            log.warn("Zamówienie {}: nie udało się wysłać maila do sprzedawcy",
                    event.orderId(), e);
        }
    }
}
