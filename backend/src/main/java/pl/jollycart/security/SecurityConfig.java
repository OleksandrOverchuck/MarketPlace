package pl.jollycart.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import pl.jollycart.user.UserRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final String FRONTEND_URL =
            "http://localhost:5501";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            UserRepository userRepository
    ) throws Exception {

        http
                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource())
                )

                .authorizeHttpRequests(auth -> auth

                        // Panel administratora - tylko ADMIN
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // Panel moderacji - MODERATOR i ADMIN
                        .requestMatchers("/api/moderation/**")
                        .hasAnyRole("MODERATOR", "ADMIN")

                        // Publiczne zasoby
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/error"
                        ).permitAll()

                        // Lista własnych ogłoszeń jest dostępna tylko
                        // dla aktualnie zalogowanego użytkownika.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/offers/me"
                        ).authenticated()

                        // Publiczne API ofert
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/offers",
                                "/api/offers/**",
                                "/api/categories"
                        ).permitAll()

                        // Publiczny profil użytkownika (z ocenami)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users/*/profile"
                        ).permitAll()

                        // Rejestracja i zwykłe logowanie
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/resend-code",
                                "/api/auth/login"
                        ).permitAll()

                        // Tymczasowo pozwalamy również na GET,
                        // żeby GET /api/auth/register nie powodował
                        // przekierowania do Google.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/register"
                        ).permitAll()

                        // Google OAuth
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // Pozostałe endpointy wymagają logowania
                        .anyRequest().authenticated()
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )

                .csrf(csrf -> csrf.disable())

                // Niezalogowane zapytania do API dostają czyste 401,
                // a nie przekierowanie do Google (które przeglądarka
                // blokuje jako błąd CORS).
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                ),
                                request -> request
                                        .getRequestURI()
                                        .startsWith("/api/")
                        )
                )

                // Sprawdza ban / usunięcie konta / aktualną rolę
                // przy każdym żądaniu zalogowanego użytkownika.
                .addFilterBefore(
                        new UserStatusFilter(userRepository),
                        AuthorizationFilter.class
                )

                .oauth2Login(oauth2 -> oauth2

                        // NIE ustawiamy:
                        // .loginPage("/oauth2/authorization/google")

                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(
                                        customOAuth2UserService
                                )
                        )

                        .defaultSuccessUrl(
                                FRONTEND_URL
                                        + "/frontend/pages/index.html",
                                true
                        )

                        .failureHandler((request, response, exception) -> {
                            String code = "oauth_failed";

                            if (exception instanceof OAuth2AuthenticationException oauthException) {
                                code = oauthException.getError().getErrorCode();
                            }

                            response.sendRedirect(
                                    FRONTEND_URL
                                            + "/frontend/pages/login.html?error="
                                            + URLEncoder.encode(
                                                    code,
                                                    StandardCharsets.UTF_8
                                            )
                            );
                        })
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5501",
                        "http://127.0.0.1:5501"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PATCH",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        userDetailsService
                );

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider
    ) {

        return new org.springframework.security.authentication.ProviderManager(
                authenticationProvider
        );
    }
}