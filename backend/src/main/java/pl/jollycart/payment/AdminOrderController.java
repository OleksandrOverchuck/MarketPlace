package pl.jollycart.payment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Spory o zamówienia. Ścieżka /api/admin/** jest w SecurityConfig
 * dostępna tylko dla roli ADMIN.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    // refund = true  -> zwrot pieniędzy kupującemu
    // refund = false -> wypłata sprzedawcy
    public record ResolveRequest(boolean refund) {
    }

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/disputed")
    public ResponseEntity<List<OrderResponse>> disputed() {
        return ResponseEntity.ok(orderService.getDisputed());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<OrderResponse> resolve(
            @PathVariable Long id,
            @RequestBody ResolveRequest request
    ) {
        return ResponseEntity.ok(orderService.resolveDispute(id, request.refund()));
    }
}
