package pl.jollycart.payment;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/seller")
public class PaymentController {

    private final SellerAccountService sellerAccountService;

    public PaymentController(SellerAccountService sellerAccountService) {
        this.sellerAccountService = sellerAccountService;
    }

    @PostMapping("/onboarding")
    public ResponseEntity<Map<String, String>> startOnboarding(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                Map.of("url",
                        sellerAccountService.createOnboardingLink(
                                authentication.getName()))
        );
    }

    @GetMapping("/status")
    public ResponseEntity<SellerStatusResponse> getStatus(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                sellerAccountService.getStatus(authentication.getName())
        );
    }
}