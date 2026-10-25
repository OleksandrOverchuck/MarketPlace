package pl.jollycart.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stripe.Stripe;

/*
 * Terminy z opisu procesu pilnuje scheduler (co minutę), a nie sprzedawca
 * czy kupujący - dzięki temu nikt nie "zawiesi" cudzych pieniędzy.
 */
@Component
public class PaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final long autoDeliverDays;

    public PaymentScheduler(
            OrderRepository orderRepository,
            OrderService orderService,
            @Value("${app.payment.auto-deliver-days:7}") long autoDeliverDays
    ) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.autoDeliverDays = autoDeliverDays;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {

        // Bez klucza Stripe (np. środowisko bez płatności) nic nie robimy.
        if (Stripe.apiKey == null || Stripe.apiKey.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        process("wygasła płatność",
                orderRepository.findIdsPendingExpired(OrderStatus.PENDING_PAYMENT, now),
                orderService::expirePending);

        process("brak wysyłki w terminie",
                orderRepository.findIdsShipDeadlinePassed(OrderStatus.PAID, now),
                orderService::refundMissedShipment);

        process("auto-odbiór",
                orderRepository.findIdsShippedBefore(
                        OrderStatus.SHIPPED, now.minusDays(autoDeliverDays)),
                orderService::autoDeliver);

        process("auto-wypłata",
                orderRepository.findIdsConfirmDeadlinePassed(OrderStatus.DELIVERED, now),
                orderService::autoRelease);
    }

    private void process(String label, List<Long> ids, Consumer<Long> action) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                // Jedno wadliwe zamówienie nie blokuje reszty;
                // zostanie ponowione w następnym przebiegu.
                log.error("Scheduler [{}]: zamówienie {} - błąd", label, id, e);
            }
        }
    }
}
