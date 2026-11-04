package pl.jollycart.payment;

public record SellerStatusResponse(
        boolean accountCreated,     // użytkownik zaczął konfigurację
        boolean payoutsReady,       // Stripe potwierdził dane, można wypłacać
        boolean detailsSubmitted    // formularz Stripe wypełniony do końca
) {
}