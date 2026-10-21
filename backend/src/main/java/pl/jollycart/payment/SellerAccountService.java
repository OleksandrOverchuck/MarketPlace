package pl.jollycart.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;

import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;

@Service
@Transactional
public class SellerAccountService {

    private final SellerAccountRepository sellerAccountRepository;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public SellerAccountService(
            SellerAccountRepository sellerAccountRepository,
            UserRepository userRepository,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.sellerAccountRepository = sellerAccountRepository;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    // Zwraca adres formularza Stripe, w którym użytkownik poda dane i konto bankowe.
    public String createOnboardingLink(String email) {

        StripeConfig.assertConfigured();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Użytkownik nie został znaleziony"));

        try {
            SellerAccount seller = sellerAccountRepository
                    .findByUserId(user.getId())
                    .orElse(null);

            if (seller == null) {
                Account account = Account.create(
                        AccountCreateParams.builder()
                                .setType(AccountCreateParams.Type.EXPRESS)
                                .setCountry("PL")
                                .setEmail(user.getEmail())
                                .setCapabilities(
                                        AccountCreateParams.Capabilities.builder()
                                                .setTransfers(
                                                        AccountCreateParams.Capabilities.Transfers
                                                                .builder()
                                                                .setRequested(true)
                                                                .build()
                                                )
                                                .build()
                                )
                                .putMetadata("jollycartUserId", String.valueOf(user.getId()))
                                .build()
                );

                seller = sellerAccountRepository.save(
                        new SellerAccount(user.getId(), account.getId())
                );
            }

            String settingsUrl = frontendUrl + "/frontend/pages/settings.html";

            AccountLink link = AccountLink.create(
                    AccountLinkCreateParams.builder()
                            .setAccount(seller.getStripeAccountId())
                            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                            .setRefreshUrl(settingsUrl + "?payouts=refresh")
                            .setReturnUrl(settingsUrl + "?payouts=return")
                            .build()
            );

            return link.getUrl();

        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się połączyć z operatorem płatności: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public SellerStatusResponse getStatus(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Użytkownik nie został znaleziony"));

        SellerAccount seller = sellerAccountRepository
                .findByUserId(user.getId())
                .orElse(null);

        if (seller == null) {
            return new SellerStatusResponse(false, false, false);
        }

        StripeConfig.assertConfigured();

        try {
            Account account = Account.retrieve(seller.getStripeAccountId());
            return new SellerStatusResponse(
                    true,
                    isTransfersActive(account),
                    Boolean.TRUE.equals(account.getDetailsSubmitted())
            );
        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się sprawdzić statusu konta wypłat.", e);
        }
    }

    // Czy sprzedawca może dostać wypłatę (użyjemy tego później przy ofertach i kupnie).
    @Transactional(readOnly = true)
    public boolean isPayoutReady(Long userId) {

        SellerAccount seller = sellerAccountRepository
                .findByUserId(userId)
                .orElse(null);

        if (seller == null) {
            return false;
        }

        StripeConfig.assertConfigured();

        try {
            return isTransfersActive(Account.retrieve(seller.getStripeAccountId()));
        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się sprawdzić konta sprzedawcy.", e);
        }
    }

    private boolean isTransfersActive(Account account) {
        return account.getCapabilities() != null
                && "active".equals(account.getCapabilities().getTransfers());
    }
}