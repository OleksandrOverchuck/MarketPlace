package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.jollycart.offer.dto.CreateOfferRequest;
import pl.jollycart.offer.dto.OfferResponse;
import pl.jollycart.offer.dto.UpdateOfferRequest;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping
    public ResponseEntity<List<OfferResponse>> getOffers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) OfferType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return ResponseEntity.ok(
                offerService.searchOffers(
                        search,
                        categoryId,
                        type,
                        minPrice,
                        maxPrice
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferResponse> getOfferById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                offerService.getOfferById(id)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OfferResponse>> getOffersByUser(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                offerService.getOffersByUser(userId)
        );
    }

    @PostMapping
    public ResponseEntity<OfferResponse> createOffer(
            Authentication authentication,
            @Valid @RequestBody CreateOfferRequest request
    ) {
        String currentEmail = authentication.getName();

        OfferResponse response =
                offerService.createOffer(
                        currentEmail,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfferResponse> updateOffer(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateOfferRequest request
    ) {
        String currentEmail = authentication.getName();

        OfferResponse response =
                offerService.updateOffer(
                        id,
                        currentEmail,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOffer(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String currentEmail = authentication.getName();

        offerService.deleteOffer(
                id,
                currentEmail
        );

        return ResponseEntity.noContent().build();
    }
}