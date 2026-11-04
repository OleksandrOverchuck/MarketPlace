package pl.jollycart.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import pl.jollycart.payment.PriceCalculator.PriceBreakdown;

class PriceCalculatorTest {

    private PriceCalculator calculator() {
        return new PriceCalculator(
                new BigDecimal("3"),
                new BigDecimal("2.00"),
                new BigDecimal("3.3"),
                new BigDecimal("0.00")
        );
    }

    @Test
    void calculatesExampleFromSpecification() {
        PriceBreakdown b = calculator()
                .calculate(new BigDecimal("1000"), new BigDecimal("15"));

        assertEquals(new BigDecimal("1000.00"), b.itemPrice());
        assertEquals(new BigDecimal("15.00"), b.shippingCost());
        assertEquals(new BigDecimal("30.45"), b.serviceFee());
        assertEquals(new BigDecimal("35.68"), b.paymentFee());
        assertEquals(new BigDecimal("1081.13"), b.total());
        assertEquals(new BigDecimal("1015.00"), b.sellerPayout());
    }

    @Test
    void appliesMinimumServiceFee() {
        PriceBreakdown b = calculator().calculate(new BigDecimal("10"), null);

        assertEquals(new BigDecimal("2.00"), b.serviceFee());
        assertEquals(new BigDecimal("0.00"), b.shippingCost());
    }

    @Test
    void totalIsSumOfAllParts() {
        PriceBreakdown b = calculator()
                .calculate(new BigDecimal("49.99"), new BigDecimal("12.50"));

        assertEquals(
                b.itemPrice().add(b.shippingCost()).add(b.serviceFee()).add(b.paymentFee()),
                b.total()
        );
    }
}
