package pl.jollycart.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferRepository;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.OfferType;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

/*
 * Cały cykl życia płatności (escrow):
 *
 *   PENDING_PAYMENT --(zapłacono)--> PAID --(sprzedawca wysłał)--> SHIPPED
 *        |                            |                               |
 *        | (wygasło/anulowano)        | (48h bez wysyłki)             | (kupujący odebrał)
 *        v                            v                               v
 *    CANCELLED                    REFUNDED                        DELIVERED
 *                                                                     |
 *                      (potwierdzenie lub 24h bez reakcji)            |  (zgłoszenie problemu)
 *                                   v                                 v
 *                               COMPLETED  <---- admin: RELEASE ---- DISPUTED ---- admin: REFUND --> REFUNDED
 *
 * Pieniądze po zapłacie leżą na koncie platformy w Stripe (nie u sprzedawcy).
 * Dopiero przejście do COMPLETED wykonuje Transfer na konto sprzedawcy.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Minimalna kwota płatności kartą w PLN w Stripe to 2,00 zł
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("2.00");

    private final OrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final SellerAccountService sellerAccountService;

    private final String frontendUrl;
    private final String currency;
    private final long checkoutMinutes;
    private final long shipHours;
    private final long confirmHours;
    private final long autoDeliverDays;

    public OrderService(
            OrderRepository orderRepository,
            OfferRepository offerRepository,
            UserRepository userRepository,
            SellerAccountRepository sellerAccountRepository,
            SellerAccountService sellerAccountService,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.payment.currency:pln}") String currency,
            @Value("${app.payment.checkout-minutes:30}") long checkoutMinutes,
            @Value("${app.payment.ship-hours:48}") long shipHours,
            @Value("${app.payment.confirm-hours:24}") long confirmHours,
            @Value("${app.payment.auto-deliver-days:7}") long autoDeliverDays
    ) {
        this.orderRepository = orderRepository;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.sellerAccountService = sellerAccountService;
        this.frontendUrl = frontendUrl;
        this.currency = currency.toLowerCase();
        // Stripe wymaga wygaśnięcia sesji najwcześniej za 30 minut
        this.checkoutMinutes = Math.max(30, checkoutMinutes);
        this.shipHours = shipHours;
        this.confirmHours = confirmHours;
        this.autoDeliverDays = autoDeliverDays;
    }

    // =====================================================================
    //  1. KUPNO: rezerwacja oferty + sesja płatności Stripe Checkout
    // =====================================================================

    /*
     * Zwraca adres strony płatności Stripe. Oferta zostaje zarezerwowana
     * (status RESERVED), więc znika z listy i nikt inny nie może jej kupić.
     *
     * Rezerwacja jest atomowa: findByIdForUpdate blokuje wiersz oferty,
     * więc przy dwóch równoczesnych kliknięciach "Kup" drugi dostanie błąd.
     */
    @Transactional
    public OrderResponse startCheckout(String buyerEmail, Long offerId) {

        StripeConfig.assertConfigured();

        User buyer = userRepository.findByEmail(buyerEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("Użytkownik nie został znaleziony"));

        Offer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Oferta nie została znaleziona"));

        User seller = offer.getUser();

        if (offer.getType() != OfferType.SALE || !offer.isOnlinePaymentEnabled()) {
            throw new IllegalArgumentException(
                    "Ta oferta nie obsługuje płatności przez JollyCart");
        }

        if (seller.getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("Nie możesz kupić własnej oferty");
        }

        if (offer.getStatus() == OfferStatus.RESERVED) {
            throw new IllegalStateException(
                    "Ktoś właśnie płaci za tę ofertę. Spróbuj ponownie za kilka minut.");
        }

        if (offer.getStatus() != OfferStatus.ACTIVE
                || seller.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Ta oferta jest już niedostępna");
        }

        if (offer.getPrice() == null || offer.getPrice().compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException(
                    "Minimalna kwota płatności to 2,00 zł");
        }

        if (!sellerAccountService.isPayoutReady(seller.getId())) {
            throw new IllegalStateException(
                    "Sprzedawca nie ma aktywnego konta do wypłat");
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setOfferId(offer.getId());
        order.setOfferTitle(offer.getTitle());
        order.setBuyerId(buyer.getId());
        order.setSellerId(seller.getId());
        order.setAmount(offer.getPrice().setScale(2, RoundingMode.HALF_UP));
        order.setCurrency(currency);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCheckoutExpiresAt(LocalDateTime.now().plusMinutes(checkoutMinutes));
        order = orderRepository.save(order);

        offer.setStatus(OfferStatus.RESERVED);
        offerRepository.save(offer);

        try {
            String pagesUrl = frontendUrl + "/frontend/pages/orders.html";

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setCustomerEmail(buyer.getEmail())
                    .setClientReferenceId(String.valueOf(order.getId()))
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    .setExpiresAt(
                            order.getCheckoutExpiresAt()
                                    .atZone(ZoneId.systemDefault())
                                    .toEpochSecond()
                    )
                    .setSuccessUrl(pagesUrl + "?checkout=success&order=" + order.getId())
                    .setCancelUrl(pagesUrl + "?checkout=cancel&order=" + order.getId())
                    .setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                    // Grupa potrzebna do późniejszego Transferu
                                    // do sprzedawcy (separate charges and transfers).
                                    .setTransferGroup("ORDER_" + order.getId())
                                    .putMetadata("orderId", String.valueOf(order.getId()))
                                    .build()
                    )
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(currency)
                                                    .setUnitAmount(toMinorUnits(order.getAmount()))
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData
                                                                    .ProductData.builder()
                                                                    .setName(order.getOfferTitle())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(
                    params,
                    RequestOptions.builder()
                            .setIdempotencyKey("checkout-order-" + order.getId())
                            .build()
            );

            order.setStripeSessionId(session.getId());
            order.setStripeCheckoutUrl(session.getUrl());
            order = orderRepository.save(order);

            return toResponse(order, buyer.getId());

        } catch (StripeException e) {
            // Wyjątek cofa transakcję: rezerwacja oferty i zamówienie znikają.
            throw new PaymentGatewayException(
                    "Nie udało się utworzyć płatności. Spróbuj ponownie.", e);
        }
    }

    /* Kupujący rezygnuje (np. wrócił ze strony płatności przyciskiem "wstecz"). */
    @Transactional
    public OrderResponse cancelPending(String buyerEmail, Long orderId) {

        User buyer = findUser(buyerEmail);
        PurchaseOrder order = lockOrder(orderId);

        requireBuyer(order, buyer);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return toResponse(order, buyer.getId());
        }

        settleOrCancelPending(order);

        return toResponse(order, buyer.getId());
    }

    // =====================================================================
    //  2. WEBHOOK STRIPE
    // =====================================================================

    /*
     * checkout.session.completed - kupujący zapłacił.
     *
     * Nie ufamy samej treści zdarzenia: pobieramy sesję ze Stripe po ID
     * i sprawdzamy payment_status. Metoda jest idempotentna (Stripe może
     * wysłać to samo zdarzenie wielokrotnie).
     */
    @Transactional
    public void handleCheckoutCompleted(String sessionId) {

        PurchaseOrder order = orderRepository.findBySessionIdForUpdate(sessionId)
                .orElse(null);

        if (order == null) {
            log.warn("Webhook: brak zamówienia dla sesji {}", sessionId);
            return;
        }

        try {
            Session session = Session.retrieve(sessionId);

            if (!"paid".equals(session.getPaymentStatus())) {
                return;
            }

            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                applyPaid(order, session);
                log.info("Zamówienie {} opłacone", order.getId());

            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                // Zapłacono po tym, jak zamówienie zostało anulowane
                // (wyścig z wygaśnięciem) - oddajemy pieniądze.
                order.setStripePaymentIntentId(session.getPaymentIntent());
                refundPayment(order);
                order.setStatus(OrderStatus.REFUNDED);
                log.warn("Zamówienie {} zapłacone po anulowaniu - zwrot", order.getId());
            }
            // pozostałe statusy: zdarzenie już obsłużone

        } catch (StripeException e) {
            // Rzucamy dalej -> webhook zwróci 500 -> Stripe ponowi wysyłkę.
            throw new PaymentGatewayException("Błąd przetwarzania webhooka", e);
        }
    }

    /* checkout.session.expired - kupujący nie zapłacił w czasie sesji. */
    @Transactional
    public void handleCheckoutExpired(String sessionId) {

        PurchaseOrder order = orderRepository.findBySessionIdForUpdate(sessionId)
                .orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        releaseOffer(order.getOfferId(), OfferStatus.ACTIVE);
        log.info("Zamówienie {} wygasło - oferta ponownie dostępna", order.getId());
    }

    // =====================================================================
    //  3. WYSYŁKA I ODBIÓR
    // =====================================================================

    @Transactional
    public OrderResponse markShipped(String sellerEmail, Long orderId) {

        User seller = findUser(sellerEmail);
        PurchaseOrder order = lockOrder(orderId);

        if (!order.getSellerId().equals(seller.getId())) {
            throw new IllegalArgumentException("To nie jest Twoja sprzedaż");
        }

        requireStatus(order, OrderStatus.PAID,
                "Wysyłkę można oznaczyć tylko dla opłaconego zamówienia");

        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());

        return toResponse(order, seller.getId());
    }

    /* Kupujący odebrał towar - od teraz ma confirmHours na decyzję. */
    @Transactional
    public OrderResponse markReceived(String buyerEmail, Long orderId) {

        User buyer = findUser(buyerEmail);
        PurchaseOrder order = lockOrder(orderId);

        requireBuyer(order, buyer);
        requireStatus(order, OrderStatus.SHIPPED,
                "Odbiór można potwierdzić po tym, jak sprzedawca wyśle towar");

        markDelivered(order);

        return toResponse(order, buyer.getId());
    }

    /* Kupujący: "towar jest OK" -> wypłata dla sprzedawcy. */
    @Transactional
    public OrderResponse confirmOrder(String buyerEmail, Long orderId) {

        User buyer = findUser(buyerEmail);
        PurchaseOrder order = lockOrder(orderId);

        requireBuyer(order, buyer);

        if (order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Zamówienia nie można teraz potwierdzić");
        }

        releaseFunds(order);

        return toResponse(order, buyer.getId());
    }

    /* Kupujący zgłasza problem - środki zostają wstrzymane do decyzji admina. */
    @Transactional
    public OrderResponse openDispute(String buyerEmail, Long orderId, String reason) {

        User buyer = findUser(buyerEmail);
        PurchaseOrder order = lockOrder(orderId);

        requireBuyer(order, buyer);

        boolean inWindow = order.getStatus() == OrderStatus.SHIPPED
                || (order.getStatus() == OrderStatus.DELIVERED
                        && order.getConfirmDeadline() != null
                        && order.getConfirmDeadline().isAfter(LocalDateTime.now()));

        if (!inWindow) {
            throw new IllegalStateException(
                    "Zgłoszenie problemu jest już niemożliwe dla tego zamówienia");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Opisz, jaki jest problem");
        }

        order.setStatus(OrderStatus.DISPUTED);
        order.setDisputeReason(reason.trim().length() > 500
                ? reason.trim().substring(0, 500)
                : reason.trim());

        return toResponse(order, buyer.getId());
    }

    // =====================================================================
    //  4. ADMIN: rozstrzygnięcie sporu
    // =====================================================================

    @Transactional(readOnly = true)
    public List<OrderResponse> getDisputed() {
        return orderRepository.findByStatusOrderByUpdatedAtAsc(OrderStatus.DISPUTED)
                .stream()
                .map(o -> toResponse(o, null))
                .toList();
    }

    @Transactional
    public OrderResponse resolveDispute(Long orderId, boolean refundBuyer) {

        PurchaseOrder order = lockOrder(orderId);

        requireStatus(order, OrderStatus.DISPUTED, "To zamówienie nie jest w sporze");

        if (refundBuyer) {
            refundOrder(order, OfferStatus.INACTIVE);
        } else {
            releaseFunds(order);
        }

        return toResponse(order, null);
    }

    // =====================================================================
    //  5. ODCZYT
    // =====================================================================

    @Transactional(readOnly = true)
    public List<OrderResponse> getPurchases(String email) {
        User user = findUser(email);
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(o -> toResponse(o, user.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getSales(String email) {
        User user = findUser(email);
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                // Sprzedawca nie musi widzieć nieopłaconych prób zakupu
                .filter(o -> o.getStatus() != OrderStatus.PENDING_PAYMENT
                        && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> toResponse(o, user.getId()))
                .toList();
    }

    // =====================================================================
    //  6. ZADANIA CYKLICZNE (wywoływane z PaymentScheduler, każde zamówienie
    //     w osobnej transakcji - błąd jednego nie blokuje pozostałych)
    // =====================================================================

    /* Sesja płatności wygasła (a webhook nie dotarł / nie zdążył). */
    @Transactional
    public void expirePending(Long orderId) {

        PurchaseOrder order = lockOrder(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getCheckoutExpiresAt() == null
                || order.getCheckoutExpiresAt().isAfter(LocalDateTime.now())) {
            return;
        }

        settleOrCancelPending(order);
    }

    /* Sprzedawca nie wysłał towaru w terminie -> zwrot dla kupującego. */
    @Transactional
    public void refundMissedShipment(Long orderId) {

        PurchaseOrder order = lockOrder(orderId);

        if (order.getStatus() != OrderStatus.PAID
                || order.getShipDeadline() == null
                || order.getShipDeadline().isAfter(LocalDateTime.now())) {
            return;
        }

        refundOrder(order, OfferStatus.ACTIVE);
        log.info("Zamówienie {}: brak wysyłki w terminie - zwrot", order.getId());
    }

    /* Kupujący nie kliknął "Otrzymałem" w rozsądnym czasie od wysyłki. */
    @Transactional
    public void autoDeliver(Long orderId) {

        PurchaseOrder order = lockOrder(orderId);

        if (order.getStatus() != OrderStatus.SHIPPED
                || order.getShippedAt() == null
                || order.getShippedAt().isAfter(LocalDateTime.now().minusDays(autoDeliverDays))) {
            return;
        }

        markDelivered(order);
    }

    /* Minęło okno na potwierdzenie bez zgłoszenia problemu -> wypłata. */
    @Transactional
    public void autoRelease(Long orderId) {

        PurchaseOrder order = lockOrder(orderId);

        if (order.getStatus() != OrderStatus.DELIVERED
                || order.getConfirmDeadline() == null
                || order.getConfirmDeadline().isAfter(LocalDateTime.now())) {
            return;
        }

        releaseFunds(order);
        log.info("Zamówienie {}: brak reakcji kupującego - wypłata", order.getId());
    }

        /* Sprawdza w Stripe, czy zamówienie zostało opłacone (gdy webhook jeszcze nie dotarł). */
    @Transactional
    public OrderResponse syncPending(String buyerEmail, Long orderId) {

        User buyer = findUser(buyerEmail);
        PurchaseOrder order = lockOrder(orderId);

        requireBuyer(order, buyer);

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getStripeSessionId() != null) {
            try {
                Session session = Session.retrieve(order.getStripeSessionId());

                if ("complete".equals(session.getStatus())
                        && "paid".equals(session.getPaymentStatus())) {
                    applyPaid(order, session);
                }
            } catch (StripeException e) {
                throw new PaymentGatewayException(
                        "Nie udało się sprawdzić płatności.", e);
            }
        }

        return toResponse(order, buyer.getId());
    }

    // =====================================================================
    //  WNĘTRZE
    // =====================================================================

    private void applyPaid(PurchaseOrder order, Session session) throws StripeException {

        String paymentIntentId = session.getPaymentIntent();
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        LocalDateTime now = LocalDateTime.now();

        order.setStripePaymentIntentId(paymentIntentId);
        order.setStripeChargeId(paymentIntent.getLatestCharge());
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        order.setShipDeadline(now.plusHours(shipHours));
    }

    /*
     * Dla zamówienia PENDING_PAYMENT: jeśli Stripe mówi, że jednak zapłacono,
     * zapisujemy płatność; w przeciwnym razie zamykamy sesję i zwalniamy ofertę.
     */
    private void settleOrCancelPending(PurchaseOrder order) {

        try {
            if (order.getStripeSessionId() != null) {
                Session session = Session.retrieve(order.getStripeSessionId());

                if ("complete".equals(session.getStatus())
                        && "paid".equals(session.getPaymentStatus())) {
                    applyPaid(order, session);
                    return;
                }

                if ("open".equals(session.getStatus())) {
                    session.expire();
                }
            }
        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się zamknąć sesji płatności.", e);
        }

        order.setStatus(OrderStatus.CANCELLED);
        releaseOffer(order.getOfferId(), OfferStatus.ACTIVE);
    }

    private void markDelivered(PurchaseOrder order) {
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(now);
        order.setConfirmDeadline(now.plusHours(confirmHours));
    }

    /*
     * WYPŁATA: przelew ze środków platformy na konto Stripe sprzedawcy.
     *
     * source_transaction wiąże Transfer z konkretną płatnością kupującego,
     * a klucz idempotencji gwarantuje, że przy ponowieniu (np. po błędzie
     * zapisu do bazy) pieniądze nie zostaną wypłacone dwa razy.
     */
    private void releaseFunds(PurchaseOrder order) {

        if (order.getStripeTransferId() != null) {
            order.setStatus(OrderStatus.COMPLETED);
            return;
        }

        SellerAccount sellerAccount = sellerAccountRepository
                .findByUserId(order.getSellerId())
                .orElseThrow(() ->
                        new IllegalStateException("Sprzedawca nie ma konta wypłat"));

        try {
            Transfer transfer = Transfer.create(
                    TransferCreateParams.builder()
                            .setAmount(toMinorUnits(order.getAmount()))
                            .setCurrency(order.getCurrency())
                            .setDestination(sellerAccount.getStripeAccountId())
                            .setSourceTransaction(order.getStripeChargeId())
                            .setTransferGroup("ORDER_" + order.getId())
                            .putMetadata("orderId", String.valueOf(order.getId()))
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("transfer-order-" + order.getId())
                            .build()
            );

            order.setStripeTransferId(transfer.getId());

        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się wypłacić środków sprzedawcy.", e);
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        releaseOffer(order.getOfferId(), OfferStatus.SOLD);
    }

    private void refundOrder(PurchaseOrder order, OfferStatus offerStatusAfter) {
        refundPayment(order);
        order.setStatus(OrderStatus.REFUNDED);
        releaseOffer(order.getOfferId(), offerStatusAfter);
    }

    private void refundPayment(PurchaseOrder order) {

        if (order.getStripePaymentIntentId() == null) {
            throw new IllegalStateException("Brak płatności do zwrotu");
        }

        try {
            Refund.create(
                    RefundCreateParams.builder()
                            .setPaymentIntent(order.getStripePaymentIntentId())
                            .putMetadata("orderId", String.valueOf(order.getId()))
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("refund-order-" + order.getId())
                            .build()
            );
        } catch (StripeException e) {
            throw new PaymentGatewayException("Nie udało się zwrócić płatności.", e);
        }
    }

    /* Zmienia status oferty tylko, jeśli jest nadal zarezerwowana przez to zamówienie. */
    private void releaseOffer(Long offerId, OfferStatus newStatus) {
        offerRepository.findById(offerId).ifPresent(offer -> {
            if (offer.getStatus() == OfferStatus.RESERVED) {
                offer.setStatus(newStatus);
                offerRepository.save(offer);
            }
        });
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Użytkownik nie został znaleziony"));
    }

    private PurchaseOrder lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Zamówienie nie zostało znalezione"));
    }

    private void requireBuyer(PurchaseOrder order, User user) {
        if (!order.getBuyerId().equals(user.getId())) {
            throw new IllegalArgumentException("To nie jest Twoje zamówienie");
        }
    }

    private void requireStatus(PurchaseOrder order, OrderStatus expected, String message) {
        if (order.getStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }

    // Stripe liczy w groszach: 12,34 zł -> 1234
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    private OrderResponse toResponse(PurchaseOrder o, Long viewerId) {

        String buyerNick = userRepository.findById(o.getBuyerId())
                .map(User::getNickname).orElse("(usunięty użytkownik)");
        String sellerNick = userRepository.findById(o.getSellerId())
                .map(User::getNickname).orElse("(usunięty użytkownik)");

        boolean viewerIsBuyer = viewerId != null && viewerId.equals(o.getBuyerId());

        return new OrderResponse(
                o.getId(),
                o.getOfferId(),
                o.getOfferTitle(),
                o.getAmount(),
                o.getCurrency(),
                o.getStatus(),
                o.getBuyerId(),
                buyerNick,
                o.getSellerId(),
                sellerNick,
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShipDeadline(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getConfirmDeadline(),
                o.getCompletedAt(),
                o.getDisputeReason(),
                viewerIsBuyer && o.getStatus() == OrderStatus.PENDING_PAYMENT
                        ? o.getStripeCheckoutUrl()
                        : null
        );
    }
}
