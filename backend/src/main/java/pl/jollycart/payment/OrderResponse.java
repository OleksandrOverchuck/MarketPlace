package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        Long offerId,
        String offerTitle,
        // Cena towaru
        BigDecimal amount,
        // Dostawa, opłata serwisowa JollyCart, opłata operatora (szacunkowa)
        BigDecimal shippingCost,
        BigDecimal serviceFee,
        BigDecimal paymentFee,
        // Razem zapłacone przez kupującego
        BigDecimal totalAmount,
        // Ile dostaje sprzedawca (cena towaru + dostawa)
        BigDecimal sellerPayout,
        String currency,
        OrderStatus status,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime shipDeadline,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime confirmDeadline,
        LocalDateTime completedAt,
        String disputeReason,
        // Tylko dla kupującego w statusie PENDING_PAYMENT - "Dokończ płatność"
        String checkoutUrl,
        // Dane do wysyłki - widoczne dla kupującego oraz (po opłaceniu) dla sprzedawcy
        String shippingCity,
        String shippingStreet,
        String shippingPostalCode,
        String shippingPhone
) {
}
