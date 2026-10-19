package pl.jollycart.security;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import pl.jollycart.auth.AuthService;
import pl.jollycart.user.User;

@Service
public class CustomOAuth2UserService extends OidcUserService {

    private final AuthService authService;

    public CustomOAuth2UserService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public OidcUser loadUser(
            OidcUserRequest userRequest
    ) throws OAuth2AuthenticationException {

        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();

        // Łączymy konta po emailu tylko, gdy Google potwierdza ten adres
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_verified"),
                    "Adres email konta Google nie jest zweryfikowany"
            );
        }
        String providerId = oidcUser.getSubject();
        String nickname = oidcUser.getFullName();
        String avatarUrl = oidcUser.getPicture();

        User user = authService.getOrCreateGoogleUser(
                email,
                providerId,
                nickname,
                avatarUrl
        );

        if (user.isBanned()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_banned"),
                    "Twoje konto zostało zablokowane"
            );
        }

        Set<GrantedAuthority> authorities =
                new HashSet<>(oidcUser.getAuthorities());

        return new DefaultOidcUser(
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email"
        );
    }
}