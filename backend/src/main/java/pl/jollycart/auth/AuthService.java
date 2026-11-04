package pl.jollycart.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.jollycart.auth.dto.LoginRequest;
import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.User;
import pl.jollycart.user.UserRepository;
import pl.jollycart.user.dto.UserResponse;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public UserResponse register(RegisterRequest request) {

        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException(
                    "Hasła nie są zgodne"
            );
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "Email jest już zajęty"
            );
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new IllegalArgumentException(
                    "Nickname jest już zajęty"
            );
        }

        User user = new User();

        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setProviderId(null);
        user.setAvatarUrl(null);

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    public Authentication authenticate(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        // Sprawdzamy ban DOPIERO po poprawnym haśle, żeby nie ujawniać
        // osobom bez hasła, że dane konto jest zablokowane.
        if (authentication != null && authentication.getName() != null) {
            userRepository.findByEmail(authentication.getName())
                    .filter(User::isBanned)
                    .ifPresent(user -> {
                        throw new AccountBannedException(
                                user.getBanReason()
                        );
                    });
        }

        return authentication;
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Użytkownik nie został znaleziony"
                        )
                );
    }

    public User getOrCreateGoogleUser(
            String email,
            String providerId,
            String googleNickname,
            String avatarUrl
    ) {

        // 1. Użytkownik, który już logował się przez Google
        return userRepository
                .findByAuthProviderAndProviderId(
                        AuthProvider.GOOGLE,
                        providerId
                )
                // 2. Konto założone ręcznie (LOCAL) z tym samym emailem
                //    -> podpinamy do niego Google zamiast zgłaszać błąd
                .or(() -> userRepository
                        .findByEmail(email)
                        .map(existing -> linkGoogleAccount(
                                existing,
                                providerId,
                                avatarUrl
                        ))
                )
                // 3. Zupełnie nowy użytkownik
                .orElseGet(() -> {

                    User user = new User();

                    user.setEmail(email);
                    user.setAuthProvider(AuthProvider.GOOGLE);
                    user.setProviderId(providerId);
                    user.setPasswordHash(null);
                    user.setAvatarUrl(avatarUrl);

                    String nickname = createUniqueNickname(
                            googleNickname,
                            email
                    );

                    user.setNickname(nickname);

                    return userRepository.save(user);
                });
    }

    private User linkGoogleAccount(
            User existing,
            String providerId,
            String avatarUrl
    ) {

        // Email jest już powiązany z innym kontem Google - nie nadpisujemy
        if (existing.getProviderId() != null
                && !existing.getProviderId().equals(providerId)) {
            throw new IllegalArgumentException(
                    "Konto z tym adresem email jest powiązane z innym kontem Google"
            );
        }

        // authProvider zostaje bez zmian (LOCAL), dzięki temu logowanie
        // hasłem i zmiana hasła nadal działają obok logowania przez Google
        existing.setProviderId(providerId);

        if (existing.getAvatarUrl() == null && avatarUrl != null) {
            existing.setAvatarUrl(avatarUrl);
        }

        return userRepository.save(existing);
    }

    private String createUniqueNickname(
            String googleNickname,
            String email
    ) {

        String baseNickname = googleNickname;

        if (baseNickname == null || baseNickname.isBlank()) {
            baseNickname = email.substring(
                    0,
                    email.indexOf('@')
            );
        }

        baseNickname = baseNickname
                .trim()
                .replaceAll("\\s+", "_");

        if (baseNickname.length() > 30) {
            baseNickname = baseNickname.substring(0, 30);
        }

        if (!userRepository.existsByNickname(baseNickname)) {
            return baseNickname;
        }

        int counter = 1;

        while (true) {

            String suffix = "_" + counter;

            int maxBaseLength =
                    30 - suffix.length();

            String shortenedBase =
                    baseNickname.length() > maxBaseLength
                            ? baseNickname.substring(
                                    0,
                                    maxBaseLength
                            )
                            : baseNickname;

            String candidate =
                    shortenedBase + suffix;

            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }

            counter++;
        }
    }
}