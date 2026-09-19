package pl.jollycart.security;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import pl.jollycart.auth.AuthService;

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
        String providerId = oidcUser.getSubject();
        String nickname = oidcUser.getFullName();
        String avatarUrl = oidcUser.getPicture();

        authService.getOrCreateGoogleUser(
                email,
                providerId,
                nickname,
                avatarUrl
        );

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