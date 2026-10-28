package pl.jollycart.payment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Adres dostawy jest walidowany w OrderService (czytelne komunikaty po polsku)
    public record CheckoutRequest(
            @NotNull(message = "Brak oferty") Long offerId,
            String city,
            String street,
            String postalCode,
            String phone
    ) {
    }

    public record DisputeRequest(String reason) {
    }

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // "Kup przez JollyCart" -> rezerwacja oferty + adres płatności Stripe
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                orderService.startCheckout(
                        authentication.getName(),
                        request.offerId(),
                        request.city(),
                        request.street(),
                        request.postalCode(),
                        request.phone()
                )
        );
    }

    @GetMapping("/purchases")
    public ResponseEntity<List<OrderResponse>> purchases(Authentication authentication) {
        return ResponseEntity.ok(orderService.getPurchases(authentication.getName()));
    }

    @GetMapping("/sales")
    public ResponseEntity<List<OrderResponse>> sales(Authentication authentication) {
        return ResponseEntity.ok(orderService.getSales(authentication.getName()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<OrderService.UnreadOrders> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(orderService.countUnread(authentication.getName()));
    }

    @PostMapping("/mark-seen")
    public ResponseEntity<Void> markSeen(Authentication authentication) {
        orderService.markSeen(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(orderService.cancelPending(authentication.getName(), id));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<OrderResponse> sync(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(orderService.syncPending(authentication.getName(), id));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<OrderResponse> ship(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(orderService.markShipped(authentication.getName(), id));
    }

    @PostMapping("/{id}/received")
    public ResponseEntity<OrderResponse> received(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(orderService.markReceived(authentication.getName(), id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirm(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(orderService.confirmOrder(authentication.getName(), id));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<OrderResponse> dispute(
            @PathVariable Long id,
            @RequestBody DisputeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                orderService.openDispute(authentication.getName(), id, request.reason())
        );
    }
}
