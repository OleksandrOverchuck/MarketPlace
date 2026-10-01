package pl.jollycart.offer;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.jollycart.offer.dto.OfferResponse;

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

    @GetMapping("/{id}")
    public ResponseEntity<OfferResponse> getOffer(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                offerService.getOfferById(id)
        );
    }
}