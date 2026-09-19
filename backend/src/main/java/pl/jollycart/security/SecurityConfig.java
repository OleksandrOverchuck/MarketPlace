package pl.jollycart.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService
    ) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/offers",
                                "/api/offers/**",
                                "/api/categories"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()

                        // OAuth2
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()

                        // Wszystkie pozostałe endpointy
                        // wymagają zalogowania
                        .anyRequest().authenticated()
                )

                // Używamy sesji HTTP, nie JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )

                // Na tym etapie CSRF jest wyłączony
                .csrf(csrf -> csrf.disable())

                // Google OAuth2 / OpenID Connect
                .oauth2Login(oauth2 -> oauth2

                        .loginPage(
                                "/oauth2/authorization/google"
                        )

                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(
                                        customOAuth2UserService
                                )
                        )

                        // Po poprawnym logowaniu
                        // przechodzimy do endpointu
                        // zwracającego aktualnego użytkownika
                        .defaultSuccessUrl(
                                "/api/auth/me",
                                true
                        )
                );

        return http.build();
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