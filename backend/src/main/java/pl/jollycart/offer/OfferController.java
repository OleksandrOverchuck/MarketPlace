package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.jollycart.offer.dto.CreateOfferRequest;
import pl.jollycart.offer.dto.OfferResponse;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping
    public ResponseEntity<OfferResponse> createOffer(
            @Valid @RequestBody CreateOfferRequest request,
            Authentication authentication
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        offerService.createOffer(
                                authentication.getName(),
                                request
                        )
                );
    }

    @GetMapping
    public ResponseEntity<List<OfferResponse>> getOffers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) OfferType type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice
    ) {
        return ResponseEntity.ok(
                offerService.searchOffers(
                        search,
                        location,
                        categoryId,
                        type,
                        minPrice,
                        maxPrice
                )
        );
    }

    @GetMapping("/me")
    public ResponseEntity<List<OfferResponse>> getMyOffers(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                offerService.getOffersByUserEmail(
                        authentication.getName()
                )
        );
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getActiveOffersCount() {
    return ResponseEntity.ok(offerService.countActiveOffers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferResponse> getOffer(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                offerService.getOfferById(id)
        );
    }
}