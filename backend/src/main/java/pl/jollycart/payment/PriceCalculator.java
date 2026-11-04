package pl.jollycart.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * Jedyne miejsce, w którym liczymy kwoty zamówienia.
 *
 *   B     = cena towaru + dostawa
 *   F     = opłata serwisowa JollyCart = procent od B, nie mniej niż minimum
 *   Razem = (B + F + stała część opłaty Stripe) / (1 - stawka Stripe)
 *           zaokrąglone W GÓRĘ do grosza
 *   Opłata operatora (Stripe) = Razem - B - F
 *
 * Dzięki liczeniu "wstecz" po potrąceniu przez Stripe jego prowizji
 * zostaje dokładnie B + F. Stawka Stripe jest SZACUNKOWA - różnica
 * względem prawdziwej prowizji obciąża platformę (albo zwiększa jej zysk).
 */
@Component
public class PriceCalculator {

    public record PriceBreakdown(
            BigDecimal itemPrice,
            BigDecimal shippingCost,
            BigDecimal serviceFee,
            BigDecimal paymentFee,
            BigDecimal total
    ) {
        /* Kwota, którą dostaje sprzedawca po zakończeniu zamówienia. */
        public BigDecimal sellerPayout() {
            return itemPrice.add(shippingCost);
        }
    }

    private final BigDecimal serviceFeeRate;
    private final BigDecimal serviceFeeMin;
    private final BigDecimal stripeRate;
    private final BigDecimal stripeFixed;

    public PriceCalculator(
            @Value("${app.payment.service-fee-percent:3}") BigDecimal serviceFeePercent,
            @Value("${app.payment.service-fee-min:2.00}") BigDecimal serviceFeeMin,
            @Value("${app.payment.stripe-fee-percent:3.3}") BigDecimal stripeFeePercent,
            @Value("${app.payment.stripe-fee-fixed:0.00}") BigDecimal stripeFeeFixed
    ) {
        this.serviceFeeRate = serviceFeePercent.movePointLeft(2);
        this.serviceFeeMin = serviceFeeMin;
        this.stripeRate = stripeFeePercent.movePointLeft(2);
        this.stripeFixed = stripeFeeFixed;

        if (stripeRate.signum() < 0 || stripeRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalStateException(
                    "app.payment.stripe-fee-percent musi być z zakresu [0, 100)");
        }
    }

    public PriceBreakdown calculate(BigDecimal itemPrice, BigDecimal shippingCost) {

        BigDecimal price = itemPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = (shippingCost == null ? BigDecimal.ZERO : shippingCost)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal base = price.add(shipping);

        BigDecimal serviceFee = base.multiply(serviceFeeRate)
                .setScale(2, RoundingMode.HALF_UP)
                .max(serviceFeeMin.setScale(2, RoundingMode.HALF_UP));

        BigDecimal net = base.add(serviceFee);

        BigDecimal total = net.add(stripeFixed)
                .divide(BigDecimal.ONE.subtract(stripeRate), 2, RoundingMode.CEILING);

        BigDecimal paymentFee = total.subtract(net);

        return new PriceBreakdown(price, shipping, serviceFee, paymentFee, total);
    }
}
