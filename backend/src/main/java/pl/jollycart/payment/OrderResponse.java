package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        Long offerId,
        String offerTitle,
        BigDecimal amount,
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
        String checkoutUrl
) {
}
