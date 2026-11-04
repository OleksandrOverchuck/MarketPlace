package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * Zdarzenie: kupujący opłacił zamówienie.
 * Same typy proste - powiadomienie i mail powstają w osobnym wątku,
 * PO zatwierdzeniu transakcji płatności (błąd maila / powiadomienia
 * nigdy nie cofnie zapisu płatności).
 */
public record OrderPaidEvent(
        Long orderId,
        Long sellerId,
        Long buyerId,
        String offerTitle,
        BigDecimal amount,
        String currency,
        LocalDateTime shipDeadline,
        long shipHours
) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /* "2 dni" / "1 dzień" / "36 godz." - ile sprzedawca ma czasu na wysyłkę */
    public String shipWindowText() {
        if (shipHours > 0 && shipHours % 24 == 0) {
            long days = shipHours / 24;
            return days + (days == 1 ? " dzień" : " dni");
        }
        return shipHours + " godz.";
    }

    public String shipDeadlineText() {
        return shipDeadline != null ? shipDeadline.format(DATE_FORMAT) : "-";
    }

    public String amountText() {
        String unit = "pln".equalsIgnoreCase(currency)
                ? "zł"
                : currency.toUpperCase();
        return amount.setScale(2).toPlainString().replace('.', ',') + " " + unit;
    }
}
