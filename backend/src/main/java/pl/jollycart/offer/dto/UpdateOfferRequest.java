package pl.jollycart.offer.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.OfferType;

public record UpdateOfferRequest(

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

        @NotNull(message = "Typ oferty jest wymagany")
        OfferType type,

        @NotNull(message = "Status oferty jest wymagany")
        OfferStatus status,

        @NotNull(message = "Kategoria jest wymagana")
        Long categoryId
) {
}