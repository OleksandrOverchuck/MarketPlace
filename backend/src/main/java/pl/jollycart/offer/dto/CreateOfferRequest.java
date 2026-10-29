package pl.jollycart.offer.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.jollycart.offer.OfferType;

public record CreateOfferRequest(

        @NotBlank(message = "Tytuł jest wymagany")
        @Size(
                max = 150,
                message = "Tytuł może mieć maksymalnie 150 znaków"
        )
        String title,

        @NotBlank(message = "Opis jest wymagany")
        @Size(
                max = 5000,
                message = "Opis może mieć maksymalnie 5000 znaków"
        )
        String description,

        @DecimalMin(
                value = "0.00",
                inclusive = true,
                message = "Cena nie może być ujemna"
        )
        BigDecimal price,

        @Size(max = 150, message = "Lokalizacja może mieć maksymalnie 150 znaków")
        String location,

        @NotNull(message = "Typ oferty jest wymagany")
        OfferType type,

               @NotNull(message = "Kategoria jest wymagana")
        Long categoryId,

        // Płatność przez JollyCart (opcjonalna, tylko dla typu SALE)
        Boolean onlinePaymentEnabled,

        // Orientacyjny obszar lokalizacji (opcjonalny) - pokazywany na mapie.
        // Zakres współrzędnych sprawdza OfferService.applyLocationArea.
        Double latitude,

        Double longitude,

        @Min(value = 0, message = "Nieprawidłowy promień obszaru")
        @Max(value = 100000, message = "Nieprawidłowy promień obszaru")
        Integer locationRadiusMeters
) {

    // Zgodność ze starym wywołaniem (bez płatności), m.in. dla testów.
    public CreateOfferRequest(
            String title,
            String description,
            BigDecimal price,
            String location,
            OfferType type,
            Long categoryId
    ) {
        this(title, description, price, location, type, categoryId,
                null, null, null, null);
    }

    // Zgodność z wywołaniem bez współrzędnych.
    public CreateOfferRequest(
            String title,
            String description,
            BigDecimal price,
            String location,
            OfferType type,
            Long categoryId,
            Boolean onlinePaymentEnabled
    ) {
        this(title, description, price, location, type, categoryId,
                onlinePaymentEnabled, null, null, null);
    }
}