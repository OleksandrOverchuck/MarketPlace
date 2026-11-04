package pl.jollycart.payment;

public enum OrderStatus {

    // Kupujący jest na stronie płatności Stripe, oferta zarezerwowana
    PENDING_PAYMENT,

    // Zapłacone, środki trzymane przez Stripe; sprzedawca ma czas na wysyłkę
    PAID,

    // Sprzedawca oznaczył wysyłkę
    SHIPPED,

    // Kupujący odebrał towar; biegnie czas na potwierdzenie / zgłoszenie
    DELIVERED,

    // Kupujący potwierdził (lub minął czas) - środki wypłacone sprzedawcy
    COMPLETED,

    // Kupujący zgłosił problem - środki wstrzymane do decyzji administratora
    DISPUTED,

    // Płatność nie doszła do skutku (anulowana / wygasła)
    CANCELLED,

    // Pieniądze wróciły do kupującego
    REFUNDED
}
